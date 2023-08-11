package org.redpill.alfresco.module.jobs;

import org.alfresco.repo.admin.RepositoryState;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.VmShutdownListener;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.InitializingBean;

/**
 * A helper class for jobs. Extend it to get additional checks if the job should run and cluster support preventing the
 * job from running at the same time on two different nodes.
 *
 * @author Marcus Svartmark - Redpill-Linpro AB
 */
public abstract class ClusteredExecuter implements InitializingBean, Job {

  private static final Logger LOG = LoggerFactory.getLogger(ClusteredExecuter.class);

  //Default lock ttl 5 min 30 sec
  public static final long DEFAULT_LOCK_TTL = 330000L;

  protected ThreadLocal<String> _lockThreadLocal = new ThreadLocal<>();

  protected long lockTTL = DEFAULT_LOCK_TTL;

  protected String name;

  protected JobLockService jobLockService;

  protected TransactionService transactionService;

  protected RepositoryState repositoryState;

  /**
   * Execute the job.
   * @param jobName The job namae
   */
  protected abstract void executeInternal(String jobName);

  @Override
  public void execute(JobExecutionContext jobCtx) throws JobExecutionException {

    // Bypass if the system is in read-only mode
    if (transactionService.isReadOnly()) {
      LOG.info(getJobName() + " bypassed; the system is read-only.");
      return;
    }

    // Bypass if the system is bootstrapping
    if (repositoryState != null && repositoryState.isBootstrapping()) {
      LOG.info(getJobName() + " bypassed; the system is bootstrapping.");

      return;
    }

    if (createLock()) {
      refreshLock();
    } else {
      LOG.debug(getJobName() + " bypassed; a lock already exists.");
      return;
    }

    try {

      LOG.debug(getJobName() + " started.");


      executeInternal(getJobName());

      // Done
      if (LOG.isDebugEnabled()) {
        LOG.debug("   " + getJobName() + " completed.");
      }
    } catch (LockAcquisitionException e) {
      // Job being done by another process
      if (LOG.isDebugEnabled()) {
        LOG.debug("   " + getJobName() + " already underway.");
      }
    } catch (VmShutdownListener.VmShutdownException e) {
      // Aborted
      if (LOG.isDebugEnabled()) {
        LOG.debug("   " + getJobName() + " aborted.");
      }
    } finally {
      try {
        releaseLock();
      } catch (Exception ex) {
        // just swallow the exception here...
      }
    }
  }

  /**
   * Attempts to get the lock. If it fails, the current transaction is marked
   * for rollback.
   */
  protected void refreshLock() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Refreshing lock " + getLockQName());
    }
    String lockToken = _lockThreadLocal.get();

    if (StringUtils.isBlank(lockToken)) {
      throw new IllegalArgumentException("Must provide existing lockToken");
    }

    jobLockService.refreshLock(lockToken, getLockQName(), lockTTL);
  }

  /**
   * Release the lock
   */
  protected void releaseLock() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Releasing lock" + getLockQName());
    }
    String lockToken = _lockThreadLocal.get();

    if (StringUtils.isBlank(lockToken)) {
      throw new IllegalArgumentException("Must provide existing lockToken");
    }

    jobLockService.releaseLock(lockToken, getLockQName());

    _lockThreadLocal.remove();
  }

  /**
   * Creates the lock
   *
   * @return true if successful
   */
  protected boolean createLock() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Creating lock" + getLockQName());
    }
    String lockToken = _lockThreadLocal.get();

    if (StringUtils.isBlank(lockToken)) {
      try {
        RetryingTransactionCallback<String> txnWork = () -> jobLockService.getLock(getLockQName(), 1000);

        lockToken = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false, true);
      } catch (Exception e) {
        LOG.trace("Exception while trying to fetch lock", e);
        return false;
      }
      _lockThreadLocal.set(lockToken);
    }

    return true;
  }

  /**
   * Generates a QName for the job
   *
   * @return A QName
   */
  protected QName getLockQName() {
    QName createQName = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, getJobName());

    return createQName;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setJobLockService(JobLockService jobLockService) {
    this.jobLockService = jobLockService;
  }

  public void setLockTTL(long lockTTL) {
    this.lockTTL = lockTTL;
  }

  public void setTransactionService(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  public void setRepositoryState(RepositoryState repositoryState) {
    this.repositoryState = repositoryState;
  }

  /**
   * Gets the job name, defaults to the class name
   *
   * @return The job namae
   */
  public String getJobName() {
    final String jobName = name == null ? this.getClass().getSimpleName() : name;
    return jobName;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    PropertyCheck.mandatory(this, "jobLockService", jobLockService);
    PropertyCheck.mandatory(this, "transactionService", transactionService);
    PropertyCheck.mandatory(this, "repositoryState", repositoryState);
  }
}
