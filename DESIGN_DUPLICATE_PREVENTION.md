# Email Service - Duplicate Sync Prevention Design

## Problem Analysis

The current design has several vulnerabilities when multiple application instances run simultaneously:

1. **Race Conditions**: Multiple instances polling IMAP simultaneously can fetch the same emails
2. **No Distributed Locking**: No mechanism to prevent concurrent access to the same IMAP folder
3. **No Idempotency**: Email processing isn't guaranteed to be idempotent
4. **No Deduplication**: No mechanism to detect and prevent duplicate email storage

## Solution Architecture

### 1. Distributed Locking Strategy

#### Option A: Database-Based Locking (Recommended for Simplicity)
```java
@Entity
@Table(name = "sync_locks")
public class SyncLock {
    @Id
    private String lockKey; // e.g., "imap_sync_INBOX"
    
    private String instanceId;
    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;
    
    @Version
    private Long version; // Optimistic locking
}

@Repository
public interface SyncLockRepository extends JpaRepository<SyncLock, String> {
    @Modifying
    @Query("DELETE FROM SyncLock s WHERE s.expiresAt < :now")
    void deleteExpiredLocks(@Param("now") LocalDateTime now);
}

@Service
@RequiredArgsConstructor
public class DistributedLockService {
    private final SyncLockRepository lockRepository;
    private final String instanceId = UUID.randomUUID().toString();
    
    public boolean acquireLock(String lockKey, Duration duration) {
        try {
            SyncLock lock = new SyncLock();
            lock.setLockKey(lockKey);
            lock.setInstanceId(instanceId);
            lock.setAcquiredAt(LocalDateTime.now());
            lock.setExpiresAt(LocalDateTime.now().plus(duration));
            
            lockRepository.save(lock);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Lock already exists
            return tryAcquireExistingLock(lockKey, duration);
        }
    }
    
    public void releaseLock(String lockKey) {
        lockRepository.deleteById(lockKey);
    }
    
    private boolean tryAcquireExistingLock(String lockKey, Duration duration) {
        return lockRepository.findById(lockKey)
            .map(lock -> {
                if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
                    // Lock expired, try to acquire
                    lock.setInstanceId(instanceId);
                    lock.setAcquiredAt(LocalDateTime.now());
                    lock.setExpiresAt(LocalDateTime.now().plus(duration));
                    try {
                        lockRepository.save(lock);
                        return true;
                    } catch (OptimisticLockingFailureException e) {
                        return false;
                    }
                }
                return false;
            })
            .orElse(false);
    }
}
```

#### Option B: Redis-Based Locking (For High-Scale Deployments)
```java
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {
    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();
    
    public boolean acquireLock(String lockKey, Duration duration) {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("lock:" + lockKey, instanceId, duration);
        return Boolean.TRUE.equals(acquired);
    }
    
    public void releaseLock(String lockKey) {
        String currentHolder = redisTemplate.opsForValue().get("lock:" + lockKey);
        if (instanceId.equals(currentHolder)) {
            redisTemplate.delete("lock:" + lockKey);
        }
    }
}
```

### 2. Enhanced Email Receiver Service with Locking

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailReceiverService {
    private final Store imapStore;
    private final EmailProperties properties;
    private final EmailMessageRepository messageRepository;
    private final EmailSyncStateRepository syncStateRepository;
    private final DistributedLockService lockService;
    private final EmailEventPublisher eventPublisher;
    
    @Scheduled(fixedDelayString = "${email.imap.poll-interval}")
    public void pollEmails() {
        String lockKey = "imap_sync_" + properties.getImap().getFolder();
        
        if (!lockService.acquireLock(lockKey, Duration.ofMinutes(5))) {
            log.info("Another instance is already syncing folder: {}", 
                    properties.getImap().getFolder());
            return;
        }
        
        try {
            performEmailSync();
        } finally {
            lockService.releaseLock(lockKey);
        }
    }
    
    private void performEmailSync() {
        try {
            Folder folder = imapStore.getFolder(properties.getImap().getFolder());
            folder.open(Folder.READ_WRITE);
            
            // Get last sync state
            EmailSyncState syncState = syncStateRepository
                .findByFolderName(folder.getName())
                .orElse(new EmailSyncState(folder.getName()));
            
            // Fetch only new messages since last sync
            Message[] messages = folder.search(
                new ReceivedDateTerm(ComparisonTerm.GT, syncState.getLastSyncDate())
            );
            
            for (Message message : messages) {
                processMessage(message, folder.getName());
            }
            
            // Update sync state
            syncState.setLastSyncDate(new Date());
            syncState.setLastUidValidity(folder.getUIDValidity());
            syncStateRepository.save(syncState);
            
            folder.close(false);
        } catch (Exception e) {
            log.error("Error during email sync", e);
        }
    }
    
    @Transactional
    private void processMessage(Message message, String folderName) {
        try {
            long uid = ((UIDFolder) message.getFolder()).getUID(message);
            
            // Check if email already exists (idempotency)
            if (messageRepository.findByImapUidAndImapFolder(uid, folderName).isPresent()) {
                log.debug("Email already synced: UID={}, Folder={}", uid, folderName);
                return;
            }
            
            // Convert and save email
            EmailMessage emailMessage = convertToEmailMessage(message, uid, folderName);
            messageRepository.save(emailMessage);
            
            // Publish event
            eventPublisher.publishNewEmailEvent(emailMessage);
            
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }
}
```

### 3. Email Sync State Tracking

```java
@Entity
@Table(name = "email_sync_state")
public class EmailSyncState {
    @Id
    private String folderName;
    
    private Date lastSyncDate;
    private Long lastUidValidity;
    private Long highestUidSeen;
    private LocalDateTime lastModified;
    
    // Constructors, getters, setters
}

@Repository
public interface EmailSyncStateRepository extends JpaRepository<EmailSyncState, String> {
    Optional<EmailSyncState> findByFolderName(String folderName);
}
```

### 4. Database-Level Duplicate Prevention

```java
@Entity
@Table(name = "email_messages",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"imapUid", "imapFolder"}),
           @UniqueConstraint(columnNames = {"messageId"})
       })
public class EmailMessage {
    @Id
    @GeneratedValue(generator = "email-id-generator")
    @GenericGenerator(name = "email-id-generator", 
                     strategy = "com.igsl.group.email_service_sample.util.EmailIdGenerator")
    private String id;
    
    @Column(unique = true)
    private String messageId; // RFC822 Message-ID header
    
    @Column(nullable = false)
    private Long imapUid;
    
    @Column(nullable = false)
    private String imapFolder;
    
    // ... other fields
}
```

### 5. Custom ID Generator for Deterministic IDs

```java
public class EmailIdGenerator implements IdentifierGenerator {
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        EmailMessage email = (EmailMessage) object;
        
        // Generate deterministic ID based on IMAP UID and folder
        String baseString = email.getImapFolder() + "_" + email.getImapUid();
        return DigestUtils.sha256Hex(baseString);
    }
}
```

### 6. Leader Election Pattern (Alternative Approach)

```java
@Component
@RequiredArgsConstructor
public class LeaderElectionService {
    private final SyncLockRepository lockRepository;
    private final String instanceId = UUID.randomUUID().toString();
    private volatile boolean isLeader = false;
    
    @Scheduled(fixedDelay = 30000) // Check every 30 seconds
    public void electLeader() {
        String leaderKey = "email_sync_leader";
        
        try {
            SyncLock leaderLock = lockRepository.findById(leaderKey)
                .orElse(new SyncLock(leaderKey));
            
            if (leaderLock.getExpiresAt() == null || 
                leaderLock.getExpiresAt().isBefore(LocalDateTime.now())) {
                // Become leader
                leaderLock.setInstanceId(instanceId);
                leaderLock.setAcquiredAt(LocalDateTime.now());
                leaderLock.setExpiresAt(LocalDateTime.now().plusMinutes(1));
                lockRepository.save(leaderLock);
                isLeader = true;
            } else {
                isLeader = instanceId.equals(leaderLock.getInstanceId());
            }
        } catch (Exception e) {
            log.error("Leader election failed", e);
            isLeader = false;
        }
    }
    
    public boolean isLeader() {
        return isLeader;
    }
}

// Modified EmailReceiverService
@Scheduled(fixedDelayString = "${email.imap.poll-interval}")
public void pollEmails() {
    if (!leaderElectionService.isLeader()) {
        log.debug("Not the leader, skipping email sync");
        return;
    }
    
    performEmailSync();
}
```

### 7. Configuration Updates

```properties
# Email Sync Configuration
email.sync.lock-duration=5m
email.sync.leader-election-enabled=false
email.sync.duplicate-check-enabled=true

# Instance Configuration
spring.application.instance-id=${random.uuid}

# JPA Configuration for unique constraints
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.id.new_generator_mappings=true
```

### 8. Monitoring and Alerting

```java
@Component
@RequiredArgsConstructor
public class EmailSyncMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordSyncAttempt(boolean acquired) {
        meterRegistry.counter("email.sync.attempts", 
            "acquired", String.valueOf(acquired)).increment();
    }
    
    public void recordDuplicateDetected() {
        meterRegistry.counter("email.sync.duplicates").increment();
    }
    
    public void recordSyncDuration(long duration) {
        meterRegistry.timer("email.sync.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
```

## Implementation Recommendations

1. **For Single Database Deployments**: Use database-based locking with unique constraints
2. **For Multi-Database/Region**: Use Redis-based locking or message queue coordination
3. **For High Availability**: Implement leader election pattern
4. **For All Deployments**: 
   - Always use unique constraints on (imapUid, imapFolder)
   - Implement idempotent message processing
   - Track sync state per folder
   - Monitor for duplicate attempts

## Benefits of This Design

1. **Prevents Duplicate Syncing**: Only one instance can sync a folder at a time
2. **Idempotent Processing**: Safe to retry failed operations
3. **Database-Level Protection**: Unique constraints prevent duplicate storage
4. **Scalable**: Works with multiple instances and can scale horizontally
5. **Fault Tolerant**: Handles instance failures with lock expiration
6. **Observable**: Metrics for monitoring sync health

## Testing Strategy

1. **Unit Tests**: Test lock acquisition/release logic
2. **Integration Tests**: Test concurrent sync attempts
3. **Load Tests**: Verify behavior under high concurrency
4. **Chaos Tests**: Test instance failure scenarios