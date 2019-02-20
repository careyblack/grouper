package edu.internet2.middleware.grouper.pspng;

/*******************************************************************************
 * Copyright 2015 Internet2
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

import java.util.*;
import java.util.concurrent.TimeUnit;

import edu.internet2.middleware.grouper.audit.GrouperEngineBuiltin;
import edu.internet2.middleware.grouper.hibernate.GrouperContext;
import edu.internet2.middleware.grouper.messaging.GrouperBuiltinMessagingSystem;
import edu.internet2.middleware.grouper.pspng.lbmq.LinkedBlockingMultiQueue;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.grouperClient.messaging.*;
import org.apache.log4j.MDC;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.internet2.middleware.grouper.GrouperSession;


/** 
 * This class manages a full-sync thread for a provisioner. This is expected to be instantiated
 * within the Grouper Loader/Daemon jvm and it reads from the grouper-loader settings, but it is not
 * triggered by any changelog or message harness. 
 * 
 * Instead, full-refreshes are triggered via Quartz and the processing interacts directly with the 
 * Group registry.
 * 
 * @author Bert Bee-Lindgren
 *
 */
public class FullSyncProvisioner  {
  public static enum FULL_SYNC_COMMAND {FULL_SYNC_GROUP, CLEANUP, FULL_SYNC_ALL_GROUPS};

  // How often to print the progress of full-sync tasks
  private static final int FULL_SYNC_PROGRESS_INTERVAL_SECS = 5;

  // This string is chosen to not match any group name
  private static final String FULL_SYNC_ALL_GROUPS = "::full-sync-all-groups::";
  private static final InheritableThreadLocal<FullSyncQueueItem> currentFullSyncItem
              = new InheritableThreadLocal<FullSyncQueueItem>();

  final private Logger LOG;

  final Object grouperMessagingQueueSetupLock = new Object();
  
  final protected Provisioner<?,?,?> provisioner;

  /**
   * There are several sources for FullSync operations. This LinkedBlockingMultiQueue
   * is the single data structure the FullSync Engine listens to for work. FullSyncQueueItems
   * are put into "subqueues" within the MultiQueue from either messaging, changeLog processing
   * or gsh.
   */
  final LinkedBlockingMultiQueue<String, FullSyncQueueItem> queues = new LinkedBlockingMultiQueue<>();
  // Define the queues with just a little capacity so most queued messages remain in the
  // real queuing system where other Daemons could process them, for example
  final static int QUEUE_CAPCITY=2;

  // These queues are defined in priority order (most important first)
  // The local ones are unbounded (so injecting threads are never blocked) and
  //   allow the injecting threads to monitor the progress
  public static enum QUEUE_TYPE {
    ASAP_LOCAL(10, false),
    ASAP(20, true),
    CHANGELOG(30, true),
    SCHEDULED_LOCAL(40, false),
    BACKGROUND_LOCAL(50, false),
    BACKGROUND(60, true),
    RETRY_LOCAL(90, false),
    RETRY(100, true);

    int priority; // Lower priority is put at the front of the queue
    String queueName_short;
    boolean usesGrouperMessagingQueue;
    int queueCapacity;
    QUEUE_TYPE(int priority, boolean usesGrouperMessagingQueue)
    {
      this.priority = priority;
      this.queueName_short = this.name().toLowerCase();
      this.usesGrouperMessagingQueue=usesGrouperMessagingQueue;

      // GrouperMessaging queues have limited capacity so messages stay in upstream queues
      if (usesGrouperMessagingQueue) {
        queueCapacity=QUEUE_CAPCITY;
      } else {
        queueCapacity=Integer.MAX_VALUE;
      }
    }

    String getQueueName_grouperMessaging(FullSyncProvisioner fullSyncProvisioner) {
      return String.format("pspng_full_sync_%s_%s", fullSyncProvisioner.provisioner.getConfigName(), queueName_short);
    }
  }


  final Map<String, Date> lastSuccessfulFullSyncDate = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * Constructor used by the getfullSyncer() factory method to construct a full-sync wrapper
   * around a provisioner. In other words, do not call this constructor directly.
   * 
   * @param provisioner
   */
  protected FullSyncProvisioner(Provisioner provisioner) {
    LOG = LoggerFactory.getLogger(String.format("%s.%s", getClass().getName(), provisioner.getDisplayName()));
    LOG.debug("Constructing PspngFullSyncer-{}", provisioner.getDisplayName());

    this.provisioner = provisioner;
    GrouperUtil.assertion(provisioner.fullSyncMode, "FullSync provisioners must be constructed with full-sync enabled");

    // Create the subQueues
    for ( QUEUE_TYPE queue_type : QUEUE_TYPE.values() ) {
      queues.addSubQueue(queue_type.queueName_short, queue_type.priority, queue_type.queueCapacity);
    }
  }

  public String getName() {
    return String.format ("FullSyncer(%s)", getConfigName());
  }

    /**
   * Get the FullSync thread_manageFullSyncProcessing() thread running
   */
  protected void start() {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
          PspUtils.setupNewThread();
          try {
              thread_manageFullSyncProcessing();
          } catch (Throwable t) {
              LOG.error("{}: FullSync failed", getName(), t);
          }
          finally {
              LOG.warn("{}: FullSync thread has exited", getName());
          }
      }
    }, getName() + "-Thread");
    
    t.setDaemon(true);
    t.start();

    for (QUEUE_TYPE queue_type : QUEUE_TYPE.values()) {
      if (queue_type.usesGrouperMessagingQueue) {
        startMessageReadingThread(queue_type);
      }
    }
  }

    /**
     * starts a thread that reads from a GrouperMessaging queue and puts the items in
     * a local-memory subQueue (which defines the item's eventual priority).
     *
     * The messages _are_not_ acknowledged by this thread, but are instead acknowledged
     * when they are processed by the FullSync engine. Therefore, if the jvm dies
     * while the messages are in-memory, they'll be retried eventually because they
     * still live within GrouperMessaging.
     *
     * @param queue_type
     */
    protected void startMessageReadingThread(final QUEUE_TYPE queue_type) {
        // Need a copy of the provisioner for the inner, anonymous class below
        final FullSyncProvisioner theFullSyncProvisioner = this;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                PspUtils.setupNewThread();
                try {
                    thread_fullSyncMessageQueueReader(queue_type.getQueueName_grouperMessaging(theFullSyncProvisioner), queue_type.queueName_short);
                } catch (Throwable t) {
                    LOG.error("{}: Full-sync queue reader failed ({})", getName(), queue_type, t);
                }
                finally {
                    LOG.error("{}: Full-sync queue reader has exited ({})", getName(), queue_type);
                }
            }
        }, getName() + "-MessageReaderThread-" + queue_type.queueName_short);

        t.setDaemon(true);
        t.start();
    }


    /**
   * method that manages full-sync processing. Most of the time you think of FullSync as 
   * what happens on a schedule (weekly full-sync, for example). However, sometimes
   * incremental provisioning needs a specific group to be sanity checked. For example,
   * if a +member change and a -member change are batched together, the desired order 
   * can be lost. Therefore, a full-sync of the group will make sure the result is accurate.
   * 
   * This method will wait for any full-refreshes to be requested, either from incremental
   * provisioning or from a quartz-scheduled thread that triggers all the groups to be 
   * sync'ed. 
   */
  protected void thread_manageFullSyncProcessing() {
    MDC.put("who", getName()+"/");
    
    GrouperSession grouperSession = null;
    GrouperContext grouperContext = null;
    
    while (true) {
      while ( !provisioner.config.isEnabled() ) {
        LOG.warn("Provisioner is disabled. Full-sync not being done.");
        GrouperUtil.sleep(15000);
        continue;
      }

      // Start with a new session and context each time
      if ( grouperSession != null ) {
          GrouperSession.stopQuietly(grouperSession);
          grouperSession = null;
      }

      if ( grouperContext != null ) {
          GrouperContext.deleteDefaultContext();
          grouperContext = null;
      }

      // We'll create the Session & Context after we're done waiting for work to do
      // (This is okay because there is no database aspect to the (in-memory) queues
  	  FullSyncQueueItem queueItem = getNextFullSyncRequest();

      // We might have blocked for a long time on getNextFullSyncRequest(), so let's
      // double-check that we're still enabled, and skip back to top of loop if we are not
      if ( !provisioner.config.isEnabled() ) {
        continue;
      }

      grouperSession = GrouperSession.startRootSession();
      grouperContext = GrouperContext.createNewDefaultContext(GrouperEngineBuiltin.LOADER, false, true);

      LOG.info("Starting to process full-sync queue item: {}", queueItem);

      try {
        MDC.put("why", String.format("qid=%d/", queueItem.id));
        processQueueItem(queueItem);
      }
      catch (Throwable t) {
          LOG.error("{}: Full-Sync processing failed: {}", new Object[]{getName(), queueItem, t});
          queueItem.processingCompletedUnsuccessfully(false,
                  "%s: %s",
                  t.getClass().getName(), t.getMessage());

      }
      finally {
        MDC.remove("why");
      }
    }
  }

  private ProvisionerCoordinator getProvisionerCoordinator() {
        return ProvisionerFactory.getProvisionerCoordinator(provisioner);
  }


  /**
   * Method that reads a full-sync queue (See Grouper Messaging) and forwards messages
   * to an internal (in memory) subqueue. Note that the capcity of most subqueues is
   * quite limited so that these Reader threads block and leave most messages in the
   * upstream queues... so other daemons can fetch and process them.
   *
   * Note: each full-sync provisioner runs several of these, one for the different
   * of the different messaging queues
   */
  protected void thread_fullSyncMessageQueueReader(String messagingQueueName, String subQueueName) {
      MDC.put("why", String.format("full-sync-message-reader:%s/", subQueueName));
      MDC.put("who", getName() + "/");

      LOG.info("{} message reader Starting: {}-->{}",
              new Object[]{getName(), messagingQueueName, subQueueName});

      GrouperSession gs = GrouperSession.startRootSession();

      // These methods are not atomic and paralell invocation results in Duplicate Key exceptions
      // when the subject is added to groups multiple times
      synchronized (grouperMessagingQueueSetupLock) {
        GrouperBuiltinMessagingSystem.createQueue(messagingQueueName);
        if (!GrouperBuiltinMessagingSystem.allowedToSendToQueue(messagingQueueName, gs.getSubject())) {
          LOG.info("Queue permission: Granting send permission to subject={} for queue={}", messagingQueueName, gs.getSubject());
          GrouperBuiltinMessagingSystem.allowSendToQueue(messagingQueueName, gs.getSubject());
        }

        if (!GrouperBuiltinMessagingSystem.allowedToReceiveFromQueue(messagingQueueName, gs.getSubject())) {
          LOG.info("Queue permission: Granting receive permission to subject={} for queue={}", messagingQueueName, gs.getSubject());
          GrouperBuiltinMessagingSystem.allowReceiveFromQueue(messagingQueueName, gs.getSubject());
        }
      }

      // In order to remain gentle in the face of repeating errors or empty queue-pull results,
      // we want to sleep in our loop. This variable is set to different values depending on
      // what happened in our last pull-and-process loop
      int nextSleepTime_secs = 0;

      LOG.info("{} message reader: created queue {} and granted send/receive permission to {}",
              new Object[]{getName(), messagingQueueName, gs.getSubject()});

      while (true) {
          if (nextSleepTime_secs > 0) {
              GrouperUtil.sleep(1000L * nextSleepTime_secs);
          }

          // Normally, we don't want to sleep, so start each loop like nothing went wrong
          nextSleepTime_secs = 0;

          GrouperMessageReceiveParam receive = new GrouperMessageReceiveParam();
          receive.assignGrouperMessageSystemName(provisioner.getConfig().getGrouperMessagingSystemName());
          receive.assignQueueName(messagingQueueName);
          receive.assignMaxMessagesToReceiveAtOnce(QUEUE_CAPCITY);
          receive.assignLongPollMillis(300*1000);

          GrouperMessageReceiveResult grouperMessageReceiveResult = GrouperMessagingEngine.receive(receive);

          Collection<GrouperMessage> grouperMessages;
          try {
              LOG.debug("{} message reader: requesting messages from queue {}",
                      getName(), messagingQueueName);

              grouperMessages = grouperMessageReceiveResult.getGrouperMessages();
          } catch (Exception e) {
              nextSleepTime_secs = 15;
              LOG.error("{} message reader: Problem pulling messages from grouper message queue",
                      getName(), e);
              continue;
          }

          if (grouperMessages.size() == 0) {
              nextSleepTime_secs = 5;
              LOG.info("{}/{} message reader: no messages received", getName(), subQueueName);
              continue;
          }

          LOG.info("{}/{} message reader: received and processing {} messages",
                  getName(), subQueueName, grouperMessages.size());

          try {
              for (GrouperMessage message : grouperMessages) {
                  // Set up what we need to ack the message
                  GrouperMessageAcknowledgeParam messageAcknowledgeParam =
                    new GrouperMessageAcknowledgeParam()
                        .assignAcknowledgeType(GrouperMessageAcknowledgeType.mark_as_processed)
                        .assignQueueName(messagingQueueName)
                        .assignGrouperMessageSystemName(GrouperBuiltinMessagingSystem.BUILTIN_NAME)
                        .addGrouperMessage(message);

                  String body = message.getMessageBody();
                  LOG.debug("{}/{} message reader: Processing grouper message {} = {}",
                          new Object[]{getName(), subQueueName,message.getId(), body});

                  FullSyncQueueItem queueItem = FullSyncQueueItem.fromJson(getConfigName(), body);

                  if ( queueItem==null ) {
                    LOG.error("{}: Skipping invalid full-sync message: {}", getConfigName(), body);
                    GrouperMessagingEngine.acknowledge(messageAcknowledgeParam);
                    continue;
                  }
                  else {
                    queueItem.messageToAcknowledge = messageAcknowledgeParam;
                  }

                  LOG.info("{} message reader: queuing onto {} queue: {}", getName(), subQueueName, queueItem);

                  queues.getSubQueue(subQueueName).put(queueItem);
              }
          } catch (Exception e) {
              nextSleepTime_secs = 15;
              LOG.error("{} message reader: Problem while reading message and queuing it internally",
                      getName(), e);
          }
      }
  }

  protected void processQueueItem(FullSyncQueueItem queueItem) throws PspException {
    switch (queueItem.command) {
      case FULL_SYNC_GROUP:
        GrouperGroupInfo grouperGroupInfo = provisioner.getGroupInfo(queueItem.groupName);
        if ( grouperGroupInfo==null ) {
          LOG.error("{}: Group not found for full-sync: '{}'", getConfigName(), queueItem.groupName);
          queueItem.processingCompletedUnsuccessfully(false, "Group not found");
          return;
        }
        fullSyncGroup(grouperGroupInfo, queueItem);
        break;
      case CLEANUP:
        // Time to look for extra groups
        processGroupCleanup(queueItem);
        break;
      case FULL_SYNC_ALL_GROUPS:
        List<FullSyncQueueItem> scheduledItems = queueAllGroupsForFullSync(queueItem.sourceQueue, queueItem.externalReference, "Requested by message: %s", queueItem);
        for ( FullSyncQueueItem scheduledItem : scheduledItems ) {
          LOG.info("{} message reader: Group is queued for full sync: {}", getName(), scheduledItem);
        }
        break;
      default:
        queueItem.processingCompletedUnsuccessfully(false, "Full-sync command not known: %s", queueItem.command);
    }
  }


  /**
   * get the next full-sync request from our queues/subqueues.
   * 
   * This method blocks until a request is received
   */
  protected FullSyncQueueItem getNextFullSyncRequest() {
    FullSyncQueueItem result = null;
    while (result == null) {
      try {
        result = queues.poll(5, TimeUnit.MINUTES);
        if (result == null) {
          LOG.debug("{}: No full sync requests found", getName());
          continue;
        }

        // Item is not ready yet. Push it back into its queue and sleep so we
        // don't busyloop. This sleep only happens when the front of the queues
        // are not ready to be run.
        // TODO: Only sleep when we're repeatedly hammering
        if (result.wakeTimeDate!=null && result.wakeTimeDate.isAfterNow()) {
          LOG.trace("Found fullSyncQueueItem that wasn't ready. Requeuing and sleeping: {} isn't ready until {}",
                  result.id, result.wakeTimeDate);

          result.acknowledgeMessage();
          requeue(result, false);
          result = null;
          Thread.sleep(5000);
          continue;
        }
        LOG.info("{}: Next full-sync request: {}", getName(), result);

      } catch (InterruptedException e) {
        // ignore
      }
    }
    return result;
  }


  public JobStatistics startFullSyncOfAllGroupsAndWaitForCompletion() throws PspException {
      JobStatistics overallStats = new JobStatistics();

      List<FullSyncQueueItem> queuedGroupSyncs
              = queueAllGroupsForFullSync(QUEUE_TYPE.SCHEDULED_LOCAL, null,"Scheduled full sync");

      boolean everythingHasBeenCompleted = false;
      Date statusLastLoggedDate = null;

      // Monitor the progress of our full-sync task items
      int doneCount=0;
      while ( !everythingHasBeenCompleted ) {
          doneCount = 0;
          everythingHasBeenCompleted=true;
          for (FullSyncQueueItem item : queuedGroupSyncs) {
              if ( item.hasBeenProcessed() ) {
                  doneCount++;
              }
              else {
                  everythingHasBeenCompleted=false;
              }
          }

          // log the progress every FULL_SYNC_PROGRESS_INTERVAL_SECS
          if ( statusLastLoggedDate==null ||
                  (System.currentTimeMillis()-statusLastLoggedDate.getTime())/1000L > FULL_SYNC_PROGRESS_INTERVAL_SECS ) {
              LOG.info("{}: Full Sync of all groups: {} steps are done out of {} ({}%)",
                      new Object[]{getName(),
                              doneCount, queuedGroupSyncs.size(),
                              100.0 * doneCount / queuedGroupSyncs.size()});
              statusLastLoggedDate = new Date();
          }

          // Sleep for a bit to let the full-syncs progress
          if ( !everythingHasBeenCompleted ) {
              GrouperUtil.sleep(250);
          }
      }

      LOG.info("{}: Full Sync of all groups: {} steps are done out of {} ({}%)",
              new Object[]{getName(),
                      doneCount, queuedGroupSyncs.size(),
                      100.0 * doneCount / queuedGroupSyncs.size()});

      for (FullSyncQueueItem item : queuedGroupSyncs) {
          overallStats.add(item.stats);
      }
      overallStats.done();

      LOG.info("{}: Full Sync of all groups: Finished. Stats: {}", getName(), overallStats);
      return overallStats;
  }

    /**
   * Go through the Grouper Groups and queue up the ones that match the provisioner's 
   * ShouldBeProvisioned filter.
   */
  protected List<FullSyncQueueItem> queueAllGroupsForFullSync(QUEUE_TYPE queue,
                                                              String externalReference,
                                                              String reasonFormat, Object... reasonArgs) throws PspException {
    String reason = String.format(reasonFormat, reasonArgs);
    LOG.info("{}: Queuing all groups for full sync. ({})", getName(), reason);
    List<FullSyncQueueItem> result = new ArrayList<>();

    Collection<GrouperGroupInfo> allGroups = provisioner.getAllGroupsForProvisioner();
    for ( GrouperGroupInfo group : allGroups ) {
      result.add(scheduleGroupForSync(queue, group.name, externalReference, reason));
    }
    
    if ( provisioner.config.isGrouperAuthoritative()) {
        FullSyncQueueItem cleanupItem = scheduleGroupCleanup(queue, externalReference, reason);

        if ( cleanupItem != null ) {
            result.add(cleanupItem);
        }
    }

    return result;
  }
  
  
  /**
   * Put the given group in a queue for full syncing
   * @param queue : What queue should be used?
   * @param groupName
   * @param reasonArgs
   */
  public FullSyncQueueItem scheduleGroupForSync(
          QUEUE_TYPE queue,
          String groupName,
          String externalReference,
          String reasonFormat, Object... reasonArgs) {
    String reason = String.format(reasonFormat, reasonArgs);
    FullSyncQueueItem queueItem = new FullSyncQueueItem(getConfigName(), groupName, reason);
    queueItem.externalReference = externalReference;

    LOG.debug("[qid={}] Scheduling group for {} full-sync: {}: {}",
            new Object[] {
                queueItem.id,
                queue.name().toLowerCase(),
                groupName,
                reason
            }
        );
    return queue(queue, queueItem);
  }

  private String getConfigName() {
    return provisioner.getConfigName();
  }

  /**
   * Put a GROUP_CLEANUP_MARKER into the full-sync schedule. This means that
   * the target system will be checked for information about groups that either
   * no longer exist or that are no longer selected to be provisioned to the system.
   */
  public FullSyncQueueItem scheduleGroupCleanup(QUEUE_TYPE queue, String externalReference, String reasonFormat, Object... reasonArgs) {
    String reason = String.format(reasonFormat, reasonArgs);

    if ( provisioner.config.isGrouperAuthoritative() ) {
      FullSyncQueueItem queueItem = new FullSyncQueueItem(
              getConfigName(),
              FULL_SYNC_COMMAND.CLEANUP,
              reason);
      queueItem.externalReference = externalReference;
      LOG.debug("Scheduling group cleanup [{}]: {}", queue.name(), queueItem);

      return queue(queue, queueItem);
    } else {
      LOG.warn("Ignoring group-cleanup request because grouper is not authoritative within the target system");
      return null;
    }
  }

  protected FullSyncQueueItem queue(QUEUE_TYPE queue, FullSyncQueueItem queueItem)
  {
    if (queue.usesGrouperMessagingQueue)
    {
      // JSON queueItem and message it
      String queueItemJson = queueItem.toJson();
      GrouperMessageSendParam sendParam = new GrouperMessageSendParam()
              .assignGrouperMessageSystemName(provisioner.getConfig().getGrouperMessagingSystemName())
              .assignQueueOrTopicName(queue.getQueueName_grouperMessaging(this))
              .assignQueueType(GrouperMessageQueueType.queue)
              .addMessageBody(queueItemJson);
      GrouperMessagingEngine.send(sendParam);
    } else {
      try {
        queues.getSubQueue(queue.queueName_short).put(queueItem);
      } catch (InterruptedException e)
      {
        LOG.error("Interrupted while adding to in-memory queue {}/{}: {}",
                new Object[] {queue.name(), queue.queueName_short, queueItem});
      }
    }

    return queueItem;
  }


  /**
   * Requeue item either into GrouperMessaging or a local queue, depending on where the queueItem
   * came from
   * @param queueItem
   * @param processingFailed True when the event was actually processed and needs to have its
   *                         retryCount and wakeTime updated
   * @return the input queueItem after any adjustments
   */
  protected FullSyncQueueItem requeue(FullSyncQueueItem queueItem, boolean processingFailed) {
    // Put the group into the error queue

    if ( processingFailed ) {
      queueItem.incrementRetryCount();
    }

    if (queueItem.sourceQueue.usesGrouperMessagingQueue) {
      queue(QUEUE_TYPE.RETRY, queueItem);
    }
    else
      queue(QUEUE_TYPE.RETRY_LOCAL, queueItem);

    return queueItem;
  }



  /**
   * Workhorse method that handles the FullSync of a specific group.
   * @param _grouperGroupInfo Group on which to do a Full Sync. The grouper group will be reread from database to make sure information is fresh.
   * @param fullSyncQueueItem What is driving this sync
   * @return true if successful
   */
  protected boolean fullSyncGroup(GrouperGroupInfo _grouperGroupInfo, FullSyncQueueItem fullSyncQueueItem) {
      Provisioner.activeProvisioner.set(provisioner);

      // Uncache the group we're processing
      provisioner.uncacheGroup(_grouperGroupInfo, null);
      provisioner.targetSystemUserCache.clear();
      provisioner.targetSystemGroupCache.clear();

      GrouperGroupInfo grouperGroupInfo = provisioner.getGroupInfo(_grouperGroupInfo.getName());

      ProvisioningWorkItem workItem = ProvisioningWorkItem.createForFullSync(grouperGroupInfo);
      final List<ProvisioningWorkItem> workItems = Arrays.asList(workItem);

      provisioner.startCoordination(workItems);
      try {
          MDC.put("what", String.format("%s/", grouperGroupInfo));
          MDC.put("why", String.format("QID=%d/ExtRef=%s/", fullSyncQueueItem.id, fullSyncQueueItem.externalReference));
          MDC.put("step", "start/");
          LOG.info("{}: Starting Full-Sync ({}) of group {}",
                  new Object[]{getName(), fullSyncQueueItem.reason, grouperGroupInfo});

          provisioner.startProvisioningBatch(workItems);

          MDC.put("step",  "doit/");

          provisioner.setCurrentWorkItem(workItem);
          provisioner.doFullSync(grouperGroupInfo, fullSyncQueueItem.stats);

          MDC.put("step", "finsh/");
          provisioner.finishProvisioningBatch(workItems);
          provisioner.finishCoordination( workItems, true);
          fullSyncQueueItem.processingCompletedSuccessfully();
          return true;
      } catch (PspException e) {
          LOG.error("{}: Problem doing full sync. Requeuing group {}",
              new Object[]{ getName(), grouperGroupInfo, e} );

          fullSyncQueueItem.processingCompletedUnsuccessfully(true, "%s: %s", e.getClass().getName(), e.getMessage());

          requeue(fullSyncQueueItem, true);

          return false;
      } catch (Throwable e) {
          LOG.error("{}: Problem doing full sync. Requeuing group {}",
              new Object[] {getName(), grouperGroupInfo, e });
          fullSyncQueueItem.processingCompletedUnsuccessfully(true, "%s: %s", e.getClass().getName(), e.getMessage());
          requeue(fullSyncQueueItem, true);
          return false;
      }
      finally {
        provisioner.finishCoordination(workItems, false);
        MDC.remove("step");
        MDC.remove("what");
        MDC.remove("why");
      }
  }
  
  protected boolean processGroupCleanup(FullSyncQueueItem queueItem) {
    try {
      MDC.put("what", "group_cleanup/");
      MDC.put("why", String.format("QID=%d/ExtRef=%s/", queueItem.id, queueItem.externalReference));

      LOG.info("{}: Starting Group Cleanup ({})", getName(), queueItem.reason);
      ProvisioningWorkItem workItem = ProvisioningWorkItem.createForGroupCleanup();
      MDC.put("step", "start/");
      provisioner.startProvisioningBatch(Arrays.asList(workItem));

      MDC.put("step", "doit/");
      provisioner.setCurrentWorkItem(workItem);
      provisioner.prepareAndRunGroupCleanup(queueItem.stats);
      
      MDC.put("step",  "finish/");
      provisioner.finishProvisioningBatch(Arrays.asList(workItem));
      LOG.info("{}: Group-cleanup done. Stats: {}", getName(), queueItem.stats);
      queueItem.processingCompletedSuccessfully();
      return true;
    } catch (PspException e) {
      LOG.error("{}: Problem doing group cleanup",
          getName(), e );
      queueItem.processingCompletedUnsuccessfully(false, "%s: %s", e.getClass().getName(), e.getMessage());
      return false;
    }
    catch (Throwable e) {
      LOG.error("{}: Problem doing group cleanup",
          getName(), e );
      queueItem.processingCompletedUnsuccessfully(false, "%s: %s", e.getClass().getName(), e.getMessage());
      return false;
    }
    finally {
      MDC.remove("step");
      MDC.remove("what");
      MDC.remove("why");
    }
  
  }
  
}
