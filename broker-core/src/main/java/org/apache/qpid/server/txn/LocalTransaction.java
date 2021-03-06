/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.txn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

/**
 * A concrete implementation of ServerTransaction where enqueue/dequeue
 * operations share a single long-lived transaction.
 *
 * The caller is responsible for invoking commit() (or rollback()) as necessary.
 */
public class LocalTransaction implements ServerTransaction
{
    protected static final Logger _logger = LoggerFactory.getLogger(LocalTransaction.class);

    private final List<Action> _postTransactionActions = new ArrayList<Action>();

    private volatile Transaction _transaction;
    private final ActivityTimeAccessor _activityTime;
    private final MessageStore _transactionLog;
    private volatile long _txnStartTime = 0L;
    private volatile long _txnUpdateTime = 0l;
    private ListenableFuture<Void> _asyncTran;

    public LocalTransaction(MessageStore transactionLog)
    {
        this(transactionLog, new ActivityTimeAccessor()
        {
            @Override
            public long getActivityTime()
            {
                return System.currentTimeMillis();
            }
        });
    }

    public LocalTransaction(MessageStore transactionLog, ActivityTimeAccessor activityTime)
    {
        _transactionLog = transactionLog;
        _activityTime = activityTime;
    }

    @Override
    public long getTransactionStartTime()
    {
        return _txnStartTime;
    }

    @Override
    public long getTransactionUpdateTime()
    {
        return _txnUpdateTime;
    }

    public void addPostTransactionAction(Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
    }

    public void dequeue(MessageEnqueueRecord record, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        if(record != null)
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dequeue of message number " + record.getMessageNumber() + " from transaction log. Queue : " + record.getQueueId());
                }

                beginTranIfNecessary();
                _transaction.dequeueMessage(record);
            }
            catch(RuntimeException e)
            {
                tidyUpOnError(e);
            }
        }
    }

    public void dequeue(Collection<MessageInstance> queueEntries, Action postTransactionAction)
    {
        sync();
        _postTransactionActions.add(postTransactionAction);
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        try
        {
            for(MessageInstance entry : queueEntries)
            {
                final MessageEnqueueRecord record = entry.getEnqueueRecord();
                if(record != null)
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeue of message number " + record.getMessageNumber() + " from transaction log. Queue : " + record.getQueueId());
                    }

                    beginTranIfNecessary();
                    _transaction.dequeueMessage(record);
                }
            }

        }
        catch(RuntimeException e)
        {
            tidyUpOnError(e);
        }
    }

    private void tidyUpOnError(RuntimeException e)
    {
        try
        {
            doRollbackActions();
        }
        finally
        {
            try
            {
                if (_transaction != null)
                {
                    _transaction.abortTran();
                }
            }
            finally
            {
                resetDetails();
            }
        }

        throw e;
    }
    private void beginTranIfNecessary()
    {

        if(_transaction == null)
        {
            _transaction = _transactionLog.newTransaction();
        }
    }

    public void enqueue(TransactionLogResource queue, EnqueueableMessage message, EnqueueAction postTransactionAction)
    {
        sync();
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        if(queue.getMessageDurability().persist(message.isPersistent()))
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName());
                }

                beginTranIfNecessary();
                final MessageEnqueueRecord record = _transaction.enqueueMessage(queue, message);
                if(postTransactionAction != null)
                {
                    final EnqueueAction underlying = postTransactionAction;

                    _postTransactionActions.add(new Action()
                    {
                        @Override
                        public void postCommit()
                        {
                            underlying.postCommit(record);
                        }

                        @Override
                        public void onRollback()
                        {
                            underlying.onRollback();
                        }
                    });
                }
            }
            catch(RuntimeException e)
            {
                if(postTransactionAction != null)
                {
                    final EnqueueAction underlying = postTransactionAction;

                    _postTransactionActions.add(new Action()
                    {
                        @Override
                        public void postCommit()
                        {

                        }

                        @Override
                        public void onRollback()
                        {
                            underlying.onRollback();
                        }
                    });
                }
                tidyUpOnError(e);
            }
        }
        else
        {
            if(postTransactionAction != null)
            {
                final EnqueueAction underlying = postTransactionAction;
                _postTransactionActions.add(new Action()
                {
                    @Override
                    public void postCommit()
                    {
                        underlying.postCommit((MessageEnqueueRecord)null);
                    }

                    @Override
                    public void onRollback()
                    {
                        underlying.onRollback();
                    }
                });
            }
        }
    }

    public void enqueue(List<? extends BaseQueue> queues, EnqueueableMessage message, EnqueueAction postTransactionAction)
    {
        sync();
        initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime();

        try
        {
            final MessageEnqueueRecord[] records = new MessageEnqueueRecord[queues.size()];
            int i = 0;
            for(BaseQueue queue : queues)
            {
                if(queue.getMessageDurability().persist(message.isPersistent()))
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Enqueue of message number " + message.getMessageNumber() + " to transaction log. Queue : " + queue.getName() );
                    }

                    beginTranIfNecessary();
                    records[i] = _transaction.enqueueMessage(queue, message);

                }
                i++;
            }
            if(postTransactionAction != null)
            {
                final EnqueueAction underlying = postTransactionAction;

                _postTransactionActions.add(new Action()
                {
                    @Override
                    public void postCommit()
                    {
                        underlying.postCommit(records);
                    }

                    @Override
                    public void onRollback()
                    {
                        underlying.onRollback();
                    }
                });
                postTransactionAction = null;
            }
        }
        catch(RuntimeException e)
        {
            if(postTransactionAction != null)
            {
                final EnqueueAction underlying = postTransactionAction;

                _postTransactionActions.add(new Action()
                {
                    @Override
                    public void postCommit()
                    {

                    }

                    @Override
                    public void onRollback()
                    {
                        underlying.onRollback();
                    }
                });
            }
            tidyUpOnError(e);
        }
    }

    public void commit()
    {
        sync();
        commit(null);
    }

    public void commit(Runnable immediateAction)
    {
        sync();
        try
        {
            if(_transaction != null)
            {
                _transaction.commitTran();
            }

            if(immediateAction != null)
            {
                immediateAction.run();
            }

            doPostTransactionActions();
        }
        finally
        {
            resetDetails();
        }
    }

    private void doRollbackActions()
    {
        for(Action action : _postTransactionActions)
        {
            action.onRollback();
        }
    }

    public void commitAsync(final Runnable deferred)
    {
        sync();
        if(_transaction != null)
        {
            final ListenableFuture<Void> underlying = _transaction.commitTranAsync();

            /*
              Note that this future is not a general purpose future and makes assumptions about the fact that get() is
              only called once (which is enforced by how sync() works.  The post transaction actions must be performed
              in the connection thread (i.e. the thread that the sync() is called from - not the commit thread which is
              where the actions would occur if we added a listener to the underlying future
             */
            _asyncTran = new ForwardingListenableFuture<Void>()
            {

                @Override
                protected ListenableFuture<Void> delegate()
                {
                    return underlying;
                }

                @Override
                public Void get(final long timeout, final TimeUnit unit)
                        throws InterruptedException, TimeoutException, ExecutionException
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Void get() throws InterruptedException, ExecutionException
                {
                    final Void rval;
                    try
                    {
                        rval = super.get();
                        doPostTransactionActions();
                        deferred.run();
                    }
                    finally
                    {
                        resetDetails();
                    }
                    return rval;
                }



            };

        }
        else
        {
                try
                {
                    doPostTransactionActions();
                    deferred.run();
                }
                finally
                {
                    resetDetails();
                }
        }
    }

    private void doPostTransactionActions()
    {
        _logger.debug("Beginning {} post transaction actions",  _postTransactionActions.size());

        for(int i = 0; i < _postTransactionActions.size(); i++)
        {
            _postTransactionActions.get(i).postCommit();
        }

        _logger.debug("Completed post transaction actions");

    }

    public void rollback()
    {
        sync();
        try
        {
            if(_transaction != null)
            {
                _transaction.abortTran();
            }
        }
        finally
        {
            try
            {
                doRollbackActions();
            }
            finally
            {
                resetDetails();
            }
        }
    }

    public void sync()
    {
        if(_asyncTran != null)
        {
            boolean interrupted = false;
            try
            {
                while (true)
                {
                    try
                    {
                        _asyncTran.get();
                        break;
                    }
                    catch (InterruptedException e)
                    {
                        interrupted = true;
                    }

                }
            }
            catch(ExecutionException e)
            {
                if(e.getCause() instanceof RuntimeException)
                {
                    throw (RuntimeException)e.getCause();
                }
                else if(e.getCause() instanceof Error)
                {
                    throw (Error) e.getCause();
                }
                else
                {
                    throw new ServerScopedRuntimeException(e.getCause());
                }
            }
            if(interrupted)
            {
                Thread.currentThread().interrupt();
            }
            _asyncTran = null;
        }
    }

    private void initTransactionStartTimeIfNecessaryAndAdvanceUpdateTime()
    {
        long currentTime = _activityTime.getActivityTime();

        if (_txnStartTime == 0)
        {
            _txnStartTime = currentTime;
        }
        _txnUpdateTime = currentTime;
    }

    private void resetDetails()
    {
        _asyncTran = null;
        _transaction = null;
        _postTransactionActions.clear();
        _txnStartTime = 0L;
        _txnUpdateTime = 0;
    }

    public boolean isTransactional()
    {
        return true;
    }

    public interface ActivityTimeAccessor
    {
        long getActivityTime();
    }

}
