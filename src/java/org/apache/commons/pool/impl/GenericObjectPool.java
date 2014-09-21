/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimerTask;

import org.apache.commons.pool.BaseObjectPool;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolUtils;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool.ObjectTimestampPair;

/**
 * A configurable {@link ObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link PoolableObjectFactory},
 * <tt>GenericObjectPool</tt> provides robust pooling functionality for
 * arbitrary objects.
 * <p>
 * A <tt>GenericObjectPool</tt> provides a number of configurable parameters:
 * <ul>
 *  <li>
 *    {@link #setMaxActive <i>maxActive</i>} controls the maximum number of
 *    objects that can be allocated by the pool (checked out to clients, or
 *    idle awaiting checkout) at a given time.  When non-positive, there is no
 *    limit to the number of objects that can be managed by the pool at one time.
 *    When {@link #setMaxActive <i>maxActive</i>} is reached, the pool is said
 *    to be exhausted. The default setting for this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setMaxIdle <i>maxIdle</i>} controls the maximum number of objects
 *    that can sit idle in the pool at any time.  When negative, there is no
 *    limit to the number of objects that may be idle at one time. The default
 *    setting for this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} specifies the
 *    behavior of the {@link #borrowObject} method when the pool is exhausted:
 *    <ul>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_FAIL}, {@link #borrowObject} will throw
 *      a {@link NoSuchElementException}
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_GROW}, {@link #borrowObject} will create a new
 *      object and return it (essentially making {@link #setMaxActive <i>maxActive</i>}
 *      meaningless.)
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>}
 *      is {@link #WHEN_EXHAUSTED_BLOCK}, {@link #borrowObject} will block
 *      (invoke {@link Object#wait()}) until a new or idle object is available.
 *      If a positive {@link #setMaxWait <i>maxWait</i>}
 *      value is supplied, then {@link #borrowObject} will block for at
 *      most that many milliseconds, after which a {@link NoSuchElementException}
 *      will be thrown.  If {@link #setMaxWait <i>maxWait</i>} is non-positive,
 *      the {@link #borrowObject} method will block indefinitely.
 *    </li>
 *    </ul>
 *    The default <code>whenExhaustedAction</code> setting is
 *    {@link #WHEN_EXHAUSTED_BLOCK} and the default <code>maxWait</code>
 *    setting is -1. By default, therefore, <code>borrowObject</code> will
 *    block indefinitely until an idle instance becomes available.
 *  </li>
 *  <li>
 *    When {@link #setTestOnBorrow <i>testOnBorrow</i>} is set, the pool will
 *    attempt to validate each object before it is returned from the
 *    {@link #borrowObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject} method.)  Objects that fail
 *    to validate will be dropped from the pool, and a different object will
 *    be borrowed. The default setting for this parameter is
 *    <code>false.</code>
 *  </li>
 *  <li>
 *    When {@link #setTestOnReturn <i>testOnReturn</i>} is set, the pool will
 *    attempt to validate each object before it is returned to the pool in the
 *    {@link #returnObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject}
 *    method.)  Objects that fail to validate will be dropped from the pool.
 *    The default setting for this parameter is <code>false.</code>
 *  </li>
 * </ul>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects are available. This is performed by an "idle object eviction"
 * thread, which runs asynchronously. Caution should be used when configuring
 * this optional feature. Eviction runs contend with client threads for access
 * to objects in the pool, so if they run too frequently performance issues may
 * result. The idle object eviction thread may be configured using the following
 * attributes:
 * <ul>
 *  <li>
 *   {@link #setTimeBetweenEvictionRunsMillis <i>timeBetweenEvictionRunsMillis</i>}
 *   indicates how long the eviction thread should sleep before "runs" of examining
 *   idle objects.  When non-positive, no eviction thread will be launched. The
 *   default setting for this parameter is -1 (i.e., idle object eviction is
 *   disabled by default).
 *  </li>
 *  <li>
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 *   specifies the minimum amount of time that an object may sit idle in the pool
 *   before it is eligible for eviction due to idle time.  When non-positive, no object
 *   will be dropped from the pool due to idle time alone. This setting has no
 *   effect unless <code>timeBetweenEvictionRunsMillis > 0.</code> The default
 *   setting for this parameter is 30 minutes.
 *  </li>
 *  <li>
 *   {@link #setTestWhileIdle <i>testWhileIdle</i>} indicates whether or not idle
 *   objects should be validated using the factory's
 *   {@link PoolableObjectFactory#validateObject} method. Objects that fail to
 *   validate will be dropped from the pool. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis > 0.</code>  The default setting for
 *   this parameter is <code>false.</code>
 *  </li>
 *  <li>
 *   {@link #setSoftMinEvictableIdleTimeMillis <i>softMinEvictableIdleTimeMillis</i>}
 *   specifies the minimum amount of time an object may sit idle in the pool
 *   before it is eligible for eviction by the idle object evictor
 *   (if any), with the extra condition that at least "minIdle" object instances
 *   remain in the pool.  When non-positive, no objects will be evicted from the pool
 *   due to idle time alone. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis > 0.</code> and it is superceded by
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 *   (that is, if <code>minEvictableIdleTimeMillis</code> is positive, then
 *   <code>softMinEvictableIdleTimeMillis</code> is ignored). The default setting for
 *   this parameter is -1 (disabled).
 *  </li>
 *  <li>
 *   {@link #setNumTestsPerEvictionRun <i>numTestsPerEvictionRun</i>}
 *   determines the number of objects examined in each run of the idle object
 *   evictor. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis > 0.</code>  The default setting for
 *   this parameter is 3.
 *  </li>
 * </ul>
 * <p>
 * <p>
 * The pool can be configured to behave as a LIFO queue with respect to idle
 * objects - always returning the most recently used object from the pool,
 * or as a FIFO queue, where borrowObject always returns the oldest object
 * in the idle object pool.
 * <ul>
 *  <li>
 *   {@link #setLifo <i>lifo</i>}
 *   determines whether or not the pool returns idle objects in
 *   last-in-first-out order. The default setting for this parameter is
 *   <code>true.</code>
 *  </li>
 * </ul>
 * <p>
 * GenericObjectPool is not usable without a {@link PoolableObjectFactory}.  A
 * non-<code>null</code> factory must be provided either as a constructor argument
 * or via a call to {@link #setFactory} before the pool is used.
 * <p>
 * Implementation note: To prevent possible deadlocks, care has been taken to
 * ensure that no call to a factory method will occur within a synchronization
 * block. See POOL-125 and DBCP-44 for more information.
 *
 * @param <T> the type of objects held in this pool
 * 
 * @see GenericKeyedObjectPool
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @author Sandy McArthur
 * @version $Revision: 1222396 $ $Date: 2011-12-22 14:02:25 -0500 (Thu, 22 Dec 2011) $
 * @since Pool 1.0
 */
public class GenericObjectPool<T> extends BaseObjectPool<T> implements ObjectPool<T> {

    //--- public constants -------------------------------------------

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number of active objects has
     * been reached), the {@link #borrowObject}
     * method should fail, throwing a {@link NoSuchElementException}.
     * 
     * 一种“when exhuausted action” 类型,当池被耗尽的时候(即,活跃对象已经到达最大)
     * 方法borrowObject应该返回失败,然后抛出NoSuchElementException异常.
     *
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
	static final byte WHEN_EXHAUSTED_FAIL   = 0;

    /**
     * A "when exhausted action" type indicating that when the pool
     * is exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should block until a new object is available, or the
     * {@link #getMaxWait maximum wait time} has been reached.
     * 
     * 一种"when exhausted action"类型,当池被耗尽的时候(即,活跃对象已经到达最大)
     * 方法borrowObject会阻塞,直到有一个可用的对象或者已经到达MaxWait值.
     * 
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_BLOCK  = 1;

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should simply create a new object anyway.
     * 
     * 一种"when exhausted action"类型,当池被耗尽的时候(即,活跃对象已经到达最大)
	 * borrowObject方法不管怎么样都会创建一个新的对象.
	 * 
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_GROW   = 2;

    /**
     * The default cap on the number of "sleeping" instances in the pool.
     * 
     * 默认池中的最多闲置对象的个数
     * (如果超过，则直接在放入是drop掉?)
     *  
     * @see #getMaxIdle
     * @see #setMaxIdle
     */
    public static final int DEFAULT_MAX_IDLE  = 8;

    /**
     * The default minimum number of "sleeping" instances in the pool
     * before before the evictor thread (if active) spawns new objects.
     * 
     * 在逐出器线程(如果活着)产生新对象之前,默认池中最少对象的个数
     * 
     * @see #getMinIdle
     * @see #setMinIdle
     */
    public static final int DEFAULT_MIN_IDLE = 0;

    /**
     * The default cap on the total number of active instances from the pool.
     * 
     * 默认活跃对象的最大个数
     * 
     * @see #getMaxActive
     */
    public static final int DEFAULT_MAX_ACTIVE  = 8;

    /**
     * The default "when exhausted action" for the pool.
     * 
     * "when exhausted action"类型的默认值
     * 
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte DEFAULT_WHEN_EXHAUSTED_ACTION = WHEN_EXHAUSTED_BLOCK;

    /**
     * The default LIFO status. True means that borrowObject returns the
     * most recently used ("last in") idle object in the pool (if there are
     * idle instances available).  False means that the pool behaves as a FIFO
     * queue - objects are taken from the idle object pool in the order that
     * they are returned to the pool.
     * 
     * 默认LIFO状态.True的意思是borrowObject方法返回池中的最近
     * 使用过的闲置对象(如果该实例可用). Fasle的意思是池是一个
     * FIFO队列--对象从池中顺序的返回。
     * 
     * @see #setLifo
     * @since 1.4
     */
    public static final boolean DEFAULT_LIFO = true;

    /**
     * The default maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     * 
     * 当池对象被耗尽和"when exhausted action"值是WHEN_EXHAUSTED_BLOCK时
     * 默认borrowObject方法返回之前，应该等待的最大时间.
     * 
     * @see #getMaxWait
     * @see #setMaxWait
     */
    public static final long DEFAULT_MAX_WAIT = -1L;

    /**
     * The default "test on borrow" value.
     * 
     * 默认"test on borrow"值
     * 
     * @see #getTestOnBorrow
     * @see #setTestOnBorrow
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * The default "test on return" value.
     *
     * 默认"test on return"值
     * 
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * The default "test while idle" value.
     * 
     * 默认"test while idle" 值
     * 
     * @see #getTestWhileIdle
     * @see #setTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * The default "time between eviction runs" value.
     * 
     * 默认逐出器(eviction)运行的间隔时间
     * (逐出器负责确保池内对象不小于minIdle并且不大于maxIdle)
     * 
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * The default number of objects to examine per run in the idle object evictor.
     * 
     * (TODO ?)
     * 
     * @see #getNumTestsPerEvictionRun
     * @see #setNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * The default value for {@link #getMinEvictableIdleTimeMillis}.
     * 
     * (TODO ?)
     * 
     * @see #getMinEvictableIdleTimeMillis
     * @see #setMinEvictableIdleTimeMillis
     */
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L;

    /**
     * The default value for {@link #getSoftMinEvictableIdleTimeMillis}.
     * 
     * (TODO ?)
     * 
     * @see #getSoftMinEvictableIdleTimeMillis
     * @see #setSoftMinEvictableIdleTimeMillis
     */
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;

    //--- constructors -----------------------------------------------

    /**
     * Create a new <tt>GenericObjectPool</tt> with default properties.
     */
    public GenericObjectPool() {
        this(null, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified factory.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory) {
        this(factory, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param config a non-<tt>null</tt> {@link GenericObjectPool.Config} describing my configuration
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, GenericObjectPool.Config config) {
        this(factory, config.maxActive, config.whenExhaustedAction, config.maxWait, config.maxIdle, config.minIdle,
                config.testOnBorrow, config.testOnReturn, config.timeBetweenEvictionRunsMillis, 
                config.numTestsPerEvictionRun, config.minEvictableIdleTimeMillis, config.testWhileIdle, 
                config.softMinEvictableIdleTimeMillis, config.lifo);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive) {
        this(factory, maxActive, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE,
                DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW,
                DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #getTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #getTestOnReturn})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE, testOnBorrow,
                testOnReturn, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and 
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #getMaxIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait, int maxIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW,
                DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #getMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #getTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #getTestOnReturn})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, testOnBorrow, testOnReturn,
                DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and 
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before it
     * is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     *  <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     *  (see {@link #setTestWhileIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, minIdle, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle,
                DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     * @param softMinEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before it is
     * eligible for eviction with the extra condition that at least "minIdle" amount of object remain in the pool.
     * (see {@link #setSoftMinEvictableIdleTimeMillis})
     * @since Pool 1.3
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle,
            long softMinEvictableIdleTimeMillis) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, minIdle, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle,
                softMinEvictableIdleTimeMillis, DEFAULT_LIFO);
    }

    /**
     * Create a new <tt>GenericObjectPool</tt> using the specified values.
     * @param factory the (possibly <tt>null</tt>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle
     * objects for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction
     * thread (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     * @param softMinEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the
     * pool before it is eligible for eviction with the extra condition that at least "minIdle" amount of object
     * remain in the pool. (see {@link #setSoftMinEvictableIdleTimeMillis})
     * @param lifo whether or not objects are returned in last-in-first-out order from the idle object pool
     * (see {@link #setLifo})
     * @since Pool 1.4
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle,
            long softMinEvictableIdleTimeMillis, boolean lifo) {
        _factory = factory;
        _maxActive = maxActive;
        _lifo = lifo;
        switch(whenExhaustedAction) {
            case WHEN_EXHAUSTED_BLOCK:
            case WHEN_EXHAUSTED_FAIL:
            case WHEN_EXHAUSTED_GROW:
                _whenExhaustedAction = whenExhaustedAction;
                break;
            default:
                throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
        }
        _maxWait = maxWait;
        _maxIdle = maxIdle;
        _minIdle = minIdle;
        _testOnBorrow = testOnBorrow;
        _testOnReturn = testOnReturn;
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        _softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        _testWhileIdle = testWhileIdle;

        _pool = new CursorableLinkedList<ObjectTimestampPair<T>>();
        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    //--- public methods ---------------------------------------------

    //--- configuration methods --------------------------------------

    /**
     * Returns the maximum number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time.
     * When non-positive, there is no limit to the number of objects that can
     * be managed by the pool at one time.
     *
     * @return the cap on the total number of object instances managed by the pool.
     * @see #setMaxActive
     */
    public synchronized int getMaxActive() {
        return _maxActive;
    }

    /**
     * Sets the cap on the number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time. Use
     * a negative value for no limit.
     *
     * @param maxActive The cap on the total number of object instances managed by the pool.
     * Negative values mean that there is no limit to the number of objects allocated
     * by the pool.
     * @see #getMaxActive
     */
    public void setMaxActive(int maxActive) {
        synchronized(this) {
            _maxActive = maxActive;
        }
        allocate();
    }

    /**
     * Returns the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @return one of {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL} or {@link #WHEN_EXHAUSTED_GROW}
     * @see #setWhenExhaustedAction
     */
    public synchronized byte getWhenExhaustedAction() {
        return _whenExhaustedAction;
    }

    /**
     * Sets the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @param whenExhaustedAction the action code, which must be one of
     *        {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL},
     *        or {@link #WHEN_EXHAUSTED_GROW}
     * @see #getWhenExhaustedAction
     */
    public void setWhenExhaustedAction(byte whenExhaustedAction) {
        synchronized(this) {
            switch(whenExhaustedAction) {
                case WHEN_EXHAUSTED_BLOCK:
                case WHEN_EXHAUSTED_FAIL:
                case WHEN_EXHAUSTED_GROW:
                    _whenExhaustedAction = whenExhaustedAction;
                    break;
                default:
                    throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
            }
        }
        allocate();
    }


    /**
     * Returns the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @return maximum number of milliseconds to block when borrowing an object.
     * @see #setMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public synchronized long getMaxWait() {
        return _maxWait;
    }

    /**
     * Sets the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @param maxWait maximum number of milliseconds to block when borrowing an object.
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public void setMaxWait(long maxWait) {
        synchronized(this) {
            _maxWait = maxWait;
        }
        allocate();
    }

    /**
     * Returns the cap on the number of "idle" instances in the pool.
     * @return the cap on the number of "idle" instances in the pool.
     * @see #setMaxIdle
     */
    public synchronized int getMaxIdle() {
        return _maxIdle;
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool.
     * If maxIdle is set too low on heavily loaded systems it is possible you
     * will see objects being destroyed and almost immediately new objects
     * being created. This is a result of the active threads momentarily
     * returning objects faster than they are requesting them them, causing the
     * number of idle objects to rise above maxIdle. The best value for maxIdle
     * for heavily loaded system will vary but the default is a good starting
     * point.
     * @param maxIdle The cap on the number of "idle" instances in the pool.
     * Use a negative value to indicate an unlimited number of idle instances.
     * @see #getMaxIdle
     */
    public void setMaxIdle(int maxIdle) {
        synchronized(this) {
            _maxIdle = maxIdle;
        }
        allocate();
    }

    /**
     * Sets the minimum number of objects allowed in the pool
     * before the evictor thread (if active) spawns new objects.
     * Note that no objects are created when
     * <code>numActive + numIdle >= maxActive.</code>
     * This setting has no effect if the idle object evictor is disabled
     * (i.e. if <code>timeBetweenEvictionRunsMillis <= 0</code>).
     *
     * @param minIdle The minimum number of objects.
     * @see #getMinIdle
     * @see #getTimeBetweenEvictionRunsMillis()
     */
    public void setMinIdle(int minIdle) {
        synchronized(this) {
            _minIdle = minIdle;
        }
        allocate();
    }

    /**
     * Returns the minimum number of objects allowed in the pool
     * before the evictor thread (if active) spawns new objects.
     * (Note no objects are created when: numActive + numIdle >= maxActive)
     *
     * @return The minimum number of objects.
     * @see #setMinIdle
     */
    public synchronized int getMinIdle() {
        return _minIdle;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @return <code>true</code> if objects are validated before being borrowed.
     * @see #setTestOnBorrow
     */
    public boolean getTestOnBorrow() {
        return _testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @param testOnBorrow <code>true</code> if objects should be validated before being borrowed.
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        _testOnBorrow = testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @return <code>true</code> when objects will be validated after returned to {@link #returnObject}.
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @param testOnReturn <code>true</code> so objects will be validated after returned to {@link #returnObject}.
     * @see #getTestOnReturn
     */
    public void setTestOnReturn(boolean testOnReturn) {
        _testOnReturn = testOnReturn;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @return number of milliseconds to sleep between evictor runs.
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @param timeBetweenEvictionRunsMillis number of milliseconds to sleep between evictor runs.
     * @see #getTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    /**
     * Returns the max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     *
     * @return max number of objects to examine during each evictor run.
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <tt>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run.  That is, when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run. When the value is positive, the number of tests
     * actually performed in each run will be the minimum of this value and the number of instances
     * idle in the pool.
     *
     * @param numTestsPerEvictionRun max number of objects to examine during each evictor run.
     * @see #getNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     *
     * @return minimum amount of time an object may sit idle in the pool before it is eligible for eviction.
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     * @param minEvictableIdleTimeMillis minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" amount of object remain in the pool.
     *
     * @return minimum amount of time an object may sit idle in the pool before it is eligible for eviction.
     * @since Pool 1.3
     * @see #setSoftMinEvictableIdleTimeMillis
     */
    public synchronized long getSoftMinEvictableIdleTimeMillis() {
        return _softMinEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" object instances remain in the pool.
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @param softMinEvictableIdleTimeMillis minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction.
     * @since Pool 1.3
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    public synchronized void setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
        _softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @return <code>true</code> when objects will be validated by the evictor.
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @param testWhileIdle <code>true</code> so objects will be validated by the evictor.
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTestWhileIdle(boolean testWhileIdle) {
        _testWhileIdle = testWhileIdle;
    }

    /**
     * Whether or not the idle object pool acts as a LIFO queue. True means
     * that borrowObject returns the most recently used ("last in") idle object
     * in the pool (if there are idle instances available).  False means that
     * the pool behaves as a FIFO queue - objects are taken from the idle object
     * pool in the order that they are returned to the pool.
     *
     * @return <code>true</true> if the pool is configured to act as a LIFO queue
     * @since 1.4
     */
     public synchronized boolean getLifo() {
         return _lifo;
     }

     /**
      * Sets the LIFO property of the pool. True means that borrowObject returns
      * the most recently used ("last in") idle object in the pool (if there are
      * idle instances available).  False means that the pool behaves as a FIFO
      * queue - objects are taken from the idle object pool in the order that
      * they are returned to the pool.
      *
      * @param lifo the new value for the LIFO property
      * @since 1.4
      */
     public synchronized void setLifo(boolean lifo) {
         this._lifo = lifo;
     }

    /**
     * Sets my configuration.
     *
     * @param conf configuration to use.
     * @see GenericObjectPool.Config
     */
    public void setConfig(GenericObjectPool.Config conf) {
        synchronized (this) {
            setMaxIdle(conf.maxIdle);
            setMinIdle(conf.minIdle);
            setMaxActive(conf.maxActive);
            setMaxWait(conf.maxWait);
            setWhenExhaustedAction(conf.whenExhaustedAction);
            setTestOnBorrow(conf.testOnBorrow);
            setTestOnReturn(conf.testOnReturn);
            setTestWhileIdle(conf.testWhileIdle);
            setNumTestsPerEvictionRun(conf.numTestsPerEvictionRun);
            setMinEvictableIdleTimeMillis(conf.minEvictableIdleTimeMillis);
            setTimeBetweenEvictionRunsMillis(conf.timeBetweenEvictionRunsMillis);
            setSoftMinEvictableIdleTimeMillis(conf.softMinEvictableIdleTimeMillis);
            setLifo(conf.lifo);
        }
        allocate();
    }

    //-- ObjectPool methods ------------------------------------------

    /**
     * <p>Borrows an object from the pool.</p>
     * 从池中拿走一个对象
     * 
     * <p>If there is an idle instance available in the pool, then either the most-recently returned
     * (if {@link #getLifo() lifo} == true) or "oldest" (lifo == false) instance sitting idle in the pool
     * will be activated and returned.  If activation fails, or {@link #getTestOnBorrow() testOnBorrow} is set
     * to true and validation fails, the instance is destroyed and the next available instance is examined.
     * This continues until either a valid instance is returned or there are no more idle instances available.</p>
     * 如果池中有一个闲置对象,那么最近放回的(if lifo==true)或者最老的(lifo=false)对象将要被
     * 激活并返回。如果激活失败或者testOnBorrow被设置为true然后校验失败，那么这个实例会被销毁
     * 接着去检查下一个实例.
     * 这个操作会继续，直到一个可用的对象返回或者或者没有闲置对象可用
     * 
     * <p>If there are no idle instances available in the pool, behavior depends on the {@link #getMaxActive() maxActive}
     * and (if applicable) {@link #getWhenExhaustedAction() whenExhaustedAction} and {@link #getMaxWait() maxWait}
     * properties. If the number of instances checked out from the pool is less than <code>maxActive,</code> a new
     * instance is created, activated and (if applicable) validated and returned to the caller.</p>
     * 
     * <p>If the pool is exhausted (no available idle instances and no capacity to create new ones),
     * this method will either block ({@link #WHEN_EXHAUSTED_BLOCK}), throw a <code>NoSuchElementException</code>
     * ({@link #WHEN_EXHAUSTED_FAIL}), or grow ({@link #WHEN_EXHAUSTED_GROW} - ignoring maxActive).
     * The length of time that this method will block when <code>whenExhaustedAction == WHEN_EXHAUSTED_BLOCK</code>
     * is determined by the {@link #getMaxWait() maxWait} property.</p>
     * 
     * <p>When the pool is exhausted, multiple calling threads may be simultaneously blocked waiting for instances
     * to become available.  As of pool 1.5, a "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.</p>
     * 
     * @return object instance
     * @throws NoSuchElementException if an instance cannot be returned
     */
    @SuppressWarnings("deprecation")
	@Override
    public T borrowObject() throws Exception {
        long starttime = System.currentTimeMillis();
        Latch<T> latch = new Latch<T>();   //代表一个请求
        byte whenExhaustedAction;
        long maxWait;
        synchronized (this) {
            // Get local copy of current config. Can't sync when used later as
            // it can result in a deadlock. Has the added advantage that config
            // is consistent for entire method execution
            whenExhaustedAction = _whenExhaustedAction;
            maxWait = _maxWait;

            // Add this request to the queue
            // 请这个请求放入到请求对列.由下面的allocate()方法进行分配
            _allocationQueue.add(latch);
        }
        // Work the allocation queue, allocating idle instances and
        // instance creation permits in request arrival order
        //操作分配队列 ,为请求顺序分配空闲实例和实例创建许可     
        allocate();

        for(;;) {
            synchronized (this) {
                assertOpen();
            }

            // If no object was allocated from the pool above
            // 没有从池中分配出一个池对象
            if(latch.getPair() == null) {
                // check if we were allowed to create one
            	// 检查是否允许创建一个池对象
                if(latch.mayCreate()) {
                    // allow new object to be created
                	// 允许创建一个池对象
                } else {
                    // the pool is exhausted
                	// 池被耗尽时执行以下耗尽策略
                    switch(whenExhaustedAction) {
                        case WHEN_EXHAUSTED_GROW:  //该策略可以忽略不能创建新池对象的限制
                            // allow new object to be created
                            synchronized (this) {
                                // Make sure another thread didn't allocate us an object
                                // or permit a new object to be created
                            	// 确保这是其他线程没有给当前请求(latch)关联一个池对象或者授权可创建新对象
                                if (latch.getPair() == null && !latch.mayCreate()) {
                                    _allocationQueue.remove(latch); //将请求从请求队列中移除,这时该请求就变为可用请求
                                    _numInternalProcessing++;
                                }
                            }
                            break;
                        case WHEN_EXHAUSTED_FAIL:  //该策略直接抛异常
                            synchronized (this) {
                                // Make sure allocate hasn't already assigned an object
                                // in a different thread or permitted a new object to be created
                                if (latch.getPair() != null || latch.mayCreate()) {
                                    break;
                                }
                                _allocationQueue.remove(latch);
                            }
                            throw new NoSuchElementException("Pool exhausted");
                        case WHEN_EXHAUSTED_BLOCK: //该策略让请求等待一段时间
                            try {
                               	// 该同步块用latch作为锁原因：
                            	// 1.和allocate()方法中的latch同步块配合;
                            	// 2.减小锁的范围，如果都用this锁，那么走到这里时所有的线程都要
                            	// 顺序执行，但是用latch就不用，因为各个线程持有的锁(latch)不一样
                            	// 所以这里对不同的线程可以并发执行。
                                synchronized (latch) {
                                    // Before we wait, make sure another thread didn't allocate us an object
                                    // or permit a new object to be created
                                	// 等待之前确保其他线程没有给分配一个池对象或授权创建新池对象
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        if(maxWait <= 0) {  //最大等待时间小于等于0就一直等待
                                            latch.wait();
                                        } else {  //计算需要等待的时间并执行等待
                                            // this code may be executed again after a notify then continue cycle
                                            // so, need to calculate the amount of time to wait
                                            final long elapsed = (System.currentTimeMillis() - starttime);
                                            final long waitTime = maxWait - elapsed;
                                            if (waitTime > 0){
                                                latch.wait(waitTime);
                                            }
                                        }
                                    } else {
                                        break;
                                    }
                                }
                                // see if we were awakened by a closing pool
                                if(isClosed() == true) {
                                    throw new IllegalStateException("Pool closed");
                                }
                            } catch(InterruptedException e) { //被其他线程提前中断
                                boolean doAllocate = false;
                                synchronized(this) {
                                    // Need to handle the all three possibilities
                                	// 需要处理三种可能性
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        // Case 1: latch still in allocation queue
                                        // Remove latch from the allocation queue
                                    	// 情况1:请求仍然在请求队列，还没有分配池对象
                                        _allocationQueue.remove(latch);
                                    } else if (latch.getPair() == null && latch.mayCreate()) {
                                        // Case 2: latch has been given permission to create
                                        //         a new object
                                    	//情况2:请求已经被赋予可以创建新池对象
                                        _numInternalProcessing--;
                                        //腾出一个坑儿,让给别人
                                        doAllocate = true;
                                    } else {
                                        // Case 3: An object has been allocated
                                    	//  情况3:请求已经分配了一个池对象
                                        _numInternalProcessing--;
                                        _numActive++;   //下面的returnObject方法会将该值减一,所以这里先加一
                                        //以上两句可以理解为将关联池对象返回给了外部
                                        //而这语句表示将外部的池对象放入到池中
                                        returnObject(latch.getPair().getValue());
                                    }
                                }
                                if (doAllocate) {
                                    allocate();
                                }
                                
                                //发生中断异常后，中断状态已经被清除.
                                //重置线程中断状态
                                Thread.currentThread().interrupt(); //?
                                throw e;
                            }
                            
                            // 如果线程等待超时、产生虚假唤醒(没有被通知、中断或超时)
                            // 或者其他线程调用latch.notify则会走到这一步
                            if(maxWait > 0 && ((System.currentTimeMillis() - starttime) >= maxWait)) {// 超时
                                synchronized(this) {
                                    // Make sure allocate hasn't already assigned an object
                                    // in a different thread or permitted a new object to be created
                                	//超时后仍没有获取到一个关联池对象或者新创建许可
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        // Remove latch from the allocation queue
                                        _allocationQueue.remove(latch);
                                    } else {//在等待的过程中获取到一个关联池对象或者新创建许可
                                        break;
                                    }
                                }
                                throw new NoSuchElementException("Timeout waiting for idle object");
                            } else { //虚假唤醒或其它线程调用了latch.notify
                                continue; // keep looping
                            }
                        default:
                            throw new IllegalArgumentException("WhenExhaustedAction property " + whenExhaustedAction +
                                    " not recognized.");
                    }
                }
            }

            // 走到这里说明允许创建一个新池对象或者已经关联了一个池对象
            boolean newlyCreated = false;  //是否已经创建了池对象
            if(null == latch.getPair()) { //没有关联一个吃对象,但是允许创建一个池对象
                try {
                    T obj = _factory.makeObject();
                    latch.setPair(new ObjectTimestampPair<T>(obj));  //关联池对象
                    newlyCreated = true;
                } finally {
                    if (!newlyCreated) {//池对象创建失败
                        // object cannot be created
                    	// 因为之前在allocate()方法中在设置_mayCreate值时将_numInternalProcessing++
                    	// 所以如果创建池对象失败就将可用请求个数减一
                        synchronized (this) {
                            _numInternalProcessing--;
                            // No need to reset latch - about to throw exception
                        }
                        
                        // 腾出来一个坑儿，让给其他人
                        allocate();
                    }
                }
            }
            // activate & validate the object
            // 走到这里说明已经获取到了一个池对象(新创建的或者从池中拿的)
            // 激活和校验池对象是否可用
            try {
                _factory.activateObject(latch.getPair().value);
                
                //只有_testOnBorrow值为true时才对关联的池对象进行校验
                if(_testOnBorrow &&
                        !_factory.validateObject(latch.getPair().value)) {
                    throw new Exception("ValidateObject failed");
                }
                
                //池对象返回给外部前,可用请求个数减一，池外活跃对象个数加一
                synchronized(this) {
                    _numInternalProcessing--;
                    _numActive++;
                }
                
                //返回池对象
                return latch.getPair().value;
            } catch (Throwable e) { //??
                PoolUtils.checkRethrow(e);//??
                // object cannot be activated or is invalid
                // 池对象不能被激活或者校验失败
                try {
                    _factory.destroyObject(latch.getPair().value);
                } catch (Throwable e2) {
                    PoolUtils.checkRethrow(e2);
                    // cannot destroy broken object
                }
                synchronized (this) {
                    _numInternalProcessing--;
                    // 请求已关联到一个池对象，但是池对象不可用
                    if (!newlyCreated) {
                    	//重置请求,并将该请求放入到请求队列
                    	//后续再调用一次allocate()方法继续从池中拿空闲对象
                        latch.reset();
                        _allocationQueue.add(0, latch);
                    }
                }
                allocate();
                //走到这里并且newlyCreated=true
                //那么说明池中已经没有空闲对象，并且新创建的池对象不可用，那么直接抛异常
                if(newlyCreated) {
                    throw new NoSuchElementException("Could not create a validated object, cause: " + e.getMessage());
                //走到这里且newlyCreated=false
                //说明请求(latch)从池中获取的对象不可用，那么继续循环从池中寻找空闲对象
                //如果池中没有空闲对象并且也不允许再创建新池对象，那么会进入池耗尽策略switch(whenExhaustedAction)代码块
                }else {  
                    continue; // keep looping
                }
            }
        }
    }

    /**
     * Allocate available instances to latches in the allocation queue.  Then
     * set _mayCreate to true for as many additional latches remaining in queue
     * as _maxActive allows. While it is safe for GOP, for consistency with GKOP
     * this method should not be called from inside a sync block. 
     */
    private synchronized void allocate() {
        if (isClosed()) return;

        // First use any objects in the pool to clear the queue
        // 首先，用池中的对象给请求队列中的请求分配对象
        for (;;) {
            if (!_pool.isEmpty() && !_allocationQueue.isEmpty()) {//如果有请求队列有请求,且池中又空闲对象
                Latch<T> latch = _allocationQueue.removeFirst();
                latch.setPair( _pool.removeFirst());  //从池中拿走一个对象给请求(latch)
                _numInternalProcessing++;   //正在分配中得对象的个数
                synchronized (latch) {
                	//当池中对象已经耗尽且when_exhausted_block值是WHEN_EXHAUSTED_BLOCK
                	//这个时候来的请求会根据过期时间在同步块中等待。
                	//这语句让让等待(latch.wait(xxx))的请求激活
                    latch.notify();	
                }
            } else {  //跳出，直到将池中得对象分配完毕
                break;
            }
        }

        // Second  any spare capacity to create new objects
        // 然后，根据池的配置决定是否可以为请求队列中的请求(latch)创建新的池对象
        for(;;) {
        	//_maxActive 最大活跃对象个数
        	//_numActive 当前在池外的活跃对象个数
        	//_numInternalProcessing 可用请求(已分配池对象或_mayCreate标记为true的latch)的个数
            if((!_allocationQueue.isEmpty()) && (_maxActive < 0 || (_numActive + _numInternalProcessing) < _maxActive)) {
                Latch<T> latch = _allocationQueue.removeFirst();
                latch.setMayCreate(true);  //设置该请求(latch)的可创建标志为true
                _numInternalProcessing++;
                synchronized (latch) {
                	//通知等待的latch可以创建池对象了
                    latch.notify();	
                }
            } else {
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>Activation of this method decrements the active count and attempts to destroy the instance.</p>
     *  销毁一个对象
     * @throws Exception if the configured {@link PoolableObjectFactory} throws an exception destroying obj
     */
    @Override
    public void invalidateObject(T obj) throws Exception {
        try {
            if (_factory != null) {
                _factory.destroyObject(obj);
            }
        } finally {
            synchronized (this) {
                _numActive--;
            }
            allocate();
        }
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured 
     * {@link PoolableObjectFactory#destroyObject(Object)} method on each idle
     * instance. 
     * 
     * <p> Implementation notes:
     * <ul><li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being
     * returned to the idle instance pool, even during its execution. It locks
     * the pool only during instance removal. Additional instances may be returned
     * while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed.</li></ul></p>
     */
    @Override
    public void clear() {
        List<ObjectTimestampPair<T>> toDestroy = new ArrayList<ObjectTimestampPair<T>>();

        synchronized(this) {
            toDestroy.addAll(_pool);
            _numInternalProcessing = _numInternalProcessing + _pool._size;
            _pool.clear();
        }
        destroy(toDestroy, _factory);
    }

    /**
     * Private method to destroy all the objects in a collection using the 
     * supplied object factory.  Assumes that objects in the collection are
     * instances of ObjectTimestampPair and that the object instances that
     * they wrap were created by the factory.
     * 
     * @param c Collection of objects to destroy
     * @param factory PoolableConnectionFactory used to destroy the objects
     */
    private void destroy(Collection<ObjectTimestampPair<T>> c, PoolableObjectFactory<T> factory) {
        for (Iterator<ObjectTimestampPair<T>> it = c.iterator(); it.hasNext();) {
            try {
                factory.destroyObject(it.next().value);
            } catch(Exception e) {
                // ignore error, keep destroying the rest
            } finally {
                synchronized(this) {
                    _numInternalProcessing--;
                }
                allocate();
            }
        }
    }

    /**
     * Return the number of instances currently borrowed from this pool.
     *
     * @return the number of instances currently borrowed from this pool
     */
    @Override
    public synchronized int getNumActive() {
        return _numActive;
    }

    /**
     * Return the number of instances currently idle in this pool.
     *
     * @return the number of instances currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle() {
        return _pool.size();
    }

    /**
     * <p>Returns an object instance to the pool.</p>
     * 将池对象放回到池中
     * 
     * <p>If {@link #getMaxIdle() maxIdle} is set to a positive value and the number of idle instances
     * has reached this value, the returning instance is destroyed.</p>
     *  如果maxIdle被设置成一个正数并且池中idle的实例已经达到这个值,
     *  那么放回的实例会被销毁。
     * 
     * <p>If {@link #getTestOnReturn() testOnReturn} == true, the returning instance is validated before being returned
     * to the idle instance pool.  In this case, if validation fails, the instance is destroyed.</p>
     *  如果testOnReturn设置为true,那么对象在放回到池中之前会被校验,
     *  这时候如果检验失败则对象会被销毁。
     * 
     * <p><strong>Note: </strong> There is no guard to prevent an object
     * being returned to the pool multiple times. Clients are expected to
     * discard references to returned objects and ensure that an object is not
     * returned to the pool multiple times in sequence (i.e., without being
     * borrowed again between returns). Violating this contract will result in
     * the same object appearing multiple times in the pool and pool counters
     * (numActive, numIdle) returning incorrect values.</p>
     * 
     * 这里没有防止一个池对象重复放回到池中,客户端应该保证放入到池中
     * 的对象应该销毁其引用,确保一个池对象不会顺序(比如连续两次放回)
     * 的多次放入到池中。违反这个约定则一个相同对象会在池中出现多次,
     * 池的计数器(numActive,numIdle)会返回错误的值。
     * @param obj instance to return to the pool
     */
    @Override
    public void returnObject(T obj) throws Exception {
        try {
            addObjectToPool(obj, true);
        } catch (Exception e) {
            if (_factory != null) {
                try {
                    _factory.destroyObject(obj);
                } catch (Exception e2) {
                    // swallowed
                }
                // TODO: Correctness here depends on control in addObjectToPool.
                // These two methods should be refactored, removing the
                // "behavior flag", decrementNumActive, from addObjectToPool.
                // 正确性依赖于addObjectToPool
                // 这两个方法应该被重构,依次来移除decrementNumActive标记
                synchronized(this) {
                    _numActive--;
                }
                allocate();
            }
        }
    }

    /**
     * <p>Adds an object to the pool.</p>
     *  向池中添加一个对象
     * 
     * <p>Validates the object if testOnReturn == true and passivates it before returning it to the pool.
     * if validation or passivation fails, or maxIdle is set and there is no room in the pool, the instance
     * is destroyed.</p>
     * 如果testOnRetrurn参数是true则校验池对象。返回到池中之前钝化(passiveates,类似于去除初始化)池对象
     * 如果校验或钝化失败，或者池中已经没有空间(前提maxIdle被正确设置),那么该实例会被销毁。
     * 
     * <p>Calls {@link #allocate()} on successful completion</p>
     * 
     * @param obj instance to add to the pool
     * @param decrementNumActive whether or not to decrement the active count
     * @throws Exception
     */
    private void addObjectToPool(T obj, boolean decrementNumActive) throws Exception {
        boolean success = true;
        if(_testOnReturn && !(_factory.validateObject(obj))) {
            success = false; //校验失败
        } else {
            _factory.passivateObject(obj); //钝化对象
        }

        boolean shouldDestroy = !success; //校验失败就应该被销毁

        // Add instance to pool if there is room and it has passed validation
        // 如果池有空间且池对象通过校验则像池中添加一个对象
        // (if testOnreturn is set)
        boolean doAllocate = false;
        synchronized (this) {
            if (isClosed()) {
                shouldDestroy = true;
            } else {
                if((_maxIdle >= 0) && (_pool.size() >= _maxIdle)) {
                    shouldDestroy = true;  //池中没有空间,应该销毁该对象
                } else if(success) {  //可以放入到池中
                    // borrowObject always takes the first element from the queue,
                	// borrowObject 会一直拿队列的第一个元素
                    // so for LIFO, push on top, FIFO add to end
                    if (_lifo) { //后进先出(LIFO)的方式放回到池中
                        _pool.addFirst(new ObjectTimestampPair<T>(obj));
                    } else { //先进先出(FIFO)的方式放回到池中
                        _pool.addLast(new ObjectTimestampPair<T>(obj));
                    }
                    if (decrementNumActive) {
                        _numActive--;
                    }
                    doAllocate = true;
                }
            }
        }
        if (doAllocate) {
            allocate();
        }

        // Destroy the instance if necessary
        // 如果不能放回到池中,则销毁该对象
        if(shouldDestroy) {
            try {
                _factory.destroyObject(obj);
            } catch(Exception e) {
                // ignored
            }
            // Decrement active count *after* destroy if applicable
            if (decrementNumActive) {
                synchronized(this) {
                    _numActive--;
                }
                allocate();
            }
        }

    }

    /**
     * <p>Closes the pool.  Once the pool is closed, {@link #borrowObject()}
     * will fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned objects
     * destroyed on return.</p>
     * 
     * <p>Destroys idle instances in the pool by invoking {@link #clear()}.</p> 
     * 
     * @throws Exception
     */
    @Override
    public void close() throws Exception {
        super.close();
        synchronized (this) {
            clear();
            startEvictor(-1L);

            while(_allocationQueue.size() > 0) {
                Latch<T> l = _allocationQueue.removeFirst();
                
                synchronized (l) {
                    // notify the waiting thread
                    l.notify();
                }
            }
        }
    }

    /**
     * Sets the {@link PoolableObjectFactory factory} this pool uses
     * to create new instances. Trying to change
     * the <code>factory</code> while there are borrowed objects will
     * throw an {@link IllegalStateException}.  If there are instances idle
     * in the pool when this method is invoked, these will be destroyed
     * using the original factory.
     *
     * @param factory the {@link PoolableObjectFactory} used to create new instances.
     * @throws IllegalStateException when the factory cannot be set at this time
     * @deprecated to be removed in version 2.0
     */
    @Deprecated
    @Override
    public void setFactory(PoolableObjectFactory<T> factory) throws IllegalStateException {
        List<ObjectTimestampPair<T>> toDestroy = new ArrayList<ObjectTimestampPair<T>>();
        final PoolableObjectFactory<T> oldFactory = _factory;
        synchronized (this) {
            assertOpen();
            if(0 < getNumActive()) {
                throw new IllegalStateException("Objects are already active");
            } else {
                toDestroy.addAll(_pool);
                _numInternalProcessing = _numInternalProcessing + _pool._size;
                _pool.clear();
            }
            _factory = factory;
        }
        destroy(toDestroy, oldFactory); 
    }

    /**
     * <p>Perform <code>numTests</code> idle object eviction tests, evicting
     * examined objects that meet the criteria for eviction. If
     * <code>testWhileIdle</code> is true, examined objects are validated
     * when visited (and removed if invalid); otherwise only objects that
     * have been idle for more than <code>minEvicableIdletimeMillis</code>
     * are removed.</p>
     * 执行numTests空闲对象驱逐校验,驱逐那些符合驱逐标准的对象。
     * 如果testWhileIdle是true则检查对象的有效性(移除无效的);
     * 其它的那些闲置时间已经超过minEvicableIdletimeMillis的将被移除
     *
     * <p>Successive activations of this method examine objects in
     * in sequence, cycling through objects in oldest-to-youngest order.</p>
     *
     * @throws Exception if the pool is closed or eviction fails.
     */
    public void evict() throws Exception {
        assertOpen();
        synchronized (this) {
            if(_pool.isEmpty()) {
                return;
            }
            if (null == _evictionCursor) {
                _evictionCursor = _pool.cursor(_lifo ? _pool.size() : 0);
            }
        }

        for (int i=0,m=getNumTests();i<m;i++) {
            final ObjectTimestampPair<T> pair;
            synchronized (this) {  //驱逐时加锁
                if ((_lifo && !_evictionCursor.hasPrevious()) ||
                        !_lifo && !_evictionCursor.hasNext()) {
                    _evictionCursor.close();
                    //每次都从最边上的开始驱逐
                    //把游标指向最开头或最后
                    _evictionCursor = _pool.cursor(_lifo ? _pool.size() : 0); 
                }

                pair = _lifo ?
                         _evictionCursor.previous() :
                         _evictionCursor.next();
                
                //从池中驱逐最边上的对象
                _evictionCursor.remove();
                _numInternalProcessing++;  ////???
            }

            boolean removeObject = false;
            final long idleTimeMilis = System.currentTimeMillis() - pair.tstamp;
            if ((getMinEvictableIdleTimeMillis() > 0) &&
                    (idleTimeMilis > getMinEvictableIdleTimeMillis())) {
                removeObject = true;
            } else if ((getSoftMinEvictableIdleTimeMillis() > 0) &&
                    (idleTimeMilis > getSoftMinEvictableIdleTimeMillis()) &&
                    ((getNumIdle() + 1)> getMinIdle())) { // +1 accounts for object we are processing
                removeObject = true;
            }
            if(getTestWhileIdle() && !removeObject) {
                boolean active = false;
                try {
                    _factory.activateObject(pair.value);
                    active = true;
                } catch(Exception e) {
                    removeObject=true;
                }
                if(active) {
                    if(!_factory.validateObject(pair.value)) {
                        removeObject=true;
                    } else {
                        try {
                            _factory.passivateObject(pair.value);
                        } catch(Exception e) {
                            removeObject=true;
                        }
                    }
                }
            }

            if (removeObject) {
                try {
                    _factory.destroyObject(pair.value);
                } catch(Exception e) {
                    // ignored
                }
            }
            synchronized (this) {
                if(!removeObject) { //如果驱逐策略没有生效则将移除的对象还回池中
                    _evictionCursor.add(pair);
                    if (_lifo) {
                        // Skip over the element we just added back
                        _evictionCursor.previous();
                    }
                }
                _numInternalProcessing--;
            }
        }
        allocate();
    }

    /**
     * Check to see if we are below our minimum number of objects
     * if so enough to bring us back to our minimum.
     *
     * @throws Exception when {@link #addObject()} fails.
     */
    private void ensureMinIdle() throws Exception {
        // this method isn't synchronized so the
        // calculateDeficit is done at the beginning
        // as a loop limit and a second time inside the loop
        // to stop when another thread already returned the
        // needed objects
        int objectDeficit = calculateDeficit(false);
        //再次调用calculateDeficit确保不会产生多余的对象
        //方法参数为true时_numInternalProcessing参数会加一
        //加一的用途是什么？
        // 
        // 1,当有请求和池中空闲对象为0时，只有allocate方法会根据
        //     _numInternalProcessing值,来判断是否可以让该请求创建一个新对象
        // 2,假设这时_maxActive-(numActive+_numInternalProcessing)=1,_pool.size=0
        //    请求线程A调用了allocate(),驱逐线程B调用了calculateDeficit(true)
        //     2.1 如果A先执行B后执行,那么两个线程都会创建一个新对象,在创建的同时
        //    又会有锁的竞争。
        //     2.2  如果B先执行A后执行，那么B会等待知道A创建完新对象，然后A会新
        //    对象关联到B并通知B.
        // 3,如果_maxActive-(numActive+_numInternalProcessing)>1又会出现2.1的情况
        //    那么_numInternalProcessing+1的意义又是什么？？？？
        for ( int j = 0 ; j < objectDeficit && calculateDeficit(true) > 0 ; j++ ) {
            try {
                addObject();
            } finally {
                synchronized (this) {
                    _numInternalProcessing--;
                }
                allocate();
            }
        }
    }

    /**
     * This returns the number of objects to create during the pool
     * sustain cycle. This will ensure that the minimum number of idle
     * instances is maintained without going past the maxActive value.
     *
     * @param incrementInternal - Should the count of objects currently under
     *                            some form of internal processing be
     *                            incremented?
     * @return The number of objects to be created
     */
    private synchronized int calculateDeficit(boolean incrementInternal) {
        int objectDeficit = getMinIdle() - getNumIdle();
        if (_maxActive > 0) {
            int growLimit = Math.max(0,
                    getMaxActive() - getNumActive() - getNumIdle() - _numInternalProcessing);
            objectDeficit = Math.min(objectDeficit, growLimit);
        }
        if (incrementInternal && objectDeficit >0) {
            _numInternalProcessing++;    
        }
        return objectDeficit;
    }

    /**
     * Create an object, and place it into the pool.
     * addObject() is useful for "pre-loading" a pool with idle objects.
     * 
     * 创建一个对象然后放进池中
     * 
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        T obj = _factory.makeObject();
        try {
            assertOpen();
            addObjectToPool(obj, false);
        } catch (IllegalStateException ex) { // Pool closed
            try {
                _factory.destroyObject(obj);
            } catch (Exception ex2) {
                // swallow
            }
            throw ex;
        }
    }

    //--- non-public methods ----------------------------------------

    /**
     * Start the eviction thread or service, or when
     * <i>delay</i> is non-positive, stop it
     * if it is already running.
     * 
     * 开启驱逐器线程.
     * 如果参数是负数，则停止
     *
     * @param delay milliseconds between evictor runs.
     */
    protected synchronized void startEvictor(long delay) {
        if(null != _evictor) {
            EvictionTimer.cancel(_evictor);
            _evictor = null;
        }
        if(delay > 0) {
            _evictor = new Evictor();
            EvictionTimer.schedule(_evictor, delay, delay);
        }
    }

    /**
     * Returns pool info including {@link #getNumActive()}, {@link #getNumIdle()}
     * and a list of objects idle in the pool with their idle times.
     * 
     * @return string containing debug information
     */
    synchronized String debugInfo() {
        StringBuffer buf = new StringBuffer();
        buf.append("Active: ").append(getNumActive()).append("\n");
        buf.append("Idle: ").append(getNumIdle()).append("\n");
        buf.append("Idle Objects:\n");
        Iterator<ObjectTimestampPair<T>> it = _pool.iterator();
        long time = System.currentTimeMillis();
        while(it.hasNext()) {
            ObjectTimestampPair<T> pair = it.next();
            buf.append("\t").append(pair.value).append("\t").append(time - pair.tstamp).append("\n");
        }
        return buf.toString();
    }

    /** 
     * Returns the number of tests to be performed in an Evictor run,
     * based on the current value of <code>numTestsPerEvictionRun</code>
     * and the number of idle instances in the pool.
     * 
     * @see #setNumTestsPerEvictionRun
     * @return the number of tests for the Evictor to run
     */
    private int getNumTests() {
        if(_numTestsPerEvictionRun >= 0) {
            return Math.min(_numTestsPerEvictionRun, _pool.size());
        } else {
            return(int)(Math.ceil(_pool.size()/Math.abs((double)_numTestsPerEvictionRun)));
        }
    }

    //--- inner classes ----------------------------------------------

    /**
     * The idle object evictor {@link TimerTask}.
     * 闲置对象驱逐器
     * @see GenericObjectPool#setTimeBetweenEvictionRunsMillis
     */
    private class Evictor extends TimerTask {
        /**
         * Run pool maintenance.  Evict objects qualifying for eviction and then
         * invoke {@link GenericObjectPool#ensureMinIdle()}.
         */
        @Override
        public void run() {
            try {
            	//驱逐多余的对象
                evict();
            } catch(Exception e) {
                // ignored
            } catch(OutOfMemoryError oome) {
                // Log problem but give evictor thread a chance to continue in
                // case error is recoverable
                oome.printStackTrace(System.err);
            }
            try {
            	//确保池内闲置对象为minIdle
                ensureMinIdle();
            } catch(Exception e) {
                // ignored
            }
        }
    }

    /**
     * A simple "struct" encapsulating the
     * configuration information for a {@link GenericObjectPool}.
     * @see GenericObjectPool#GenericObjectPool(org.apache.commons.pool.PoolableObjectFactory,
     * org.apache.commons.pool.impl.GenericObjectPool.Config)
     * @see GenericObjectPool#setConfig
     */
    public static class Config {
        //CHECKSTYLE: stop VisibilityModifier
        /**
         * @see GenericObjectPool#setMaxIdle
         */
        public int maxIdle = GenericObjectPool.DEFAULT_MAX_IDLE;
        /**
         * @see GenericObjectPool#setMinIdle
         */
        public int minIdle = GenericObjectPool.DEFAULT_MIN_IDLE;
        /**
         * @see GenericObjectPool#setMaxActive
         */
        public int maxActive = GenericObjectPool.DEFAULT_MAX_ACTIVE;
        /**
         * @see GenericObjectPool#setMaxWait
         */
        public long maxWait = GenericObjectPool.DEFAULT_MAX_WAIT;
        /**
         * @see GenericObjectPool#setWhenExhaustedAction
         */
        public byte whenExhaustedAction = GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION;
        /**
         * @see GenericObjectPool#setTestOnBorrow
         */
        public boolean testOnBorrow = GenericObjectPool.DEFAULT_TEST_ON_BORROW;
        /**
         * @see GenericObjectPool#setTestOnReturn
         */
        public boolean testOnReturn = GenericObjectPool.DEFAULT_TEST_ON_RETURN;
        /**
         * @see GenericObjectPool#setTestWhileIdle
         */
        public boolean testWhileIdle = GenericObjectPool.DEFAULT_TEST_WHILE_IDLE;
        /**
         * @see GenericObjectPool#setTimeBetweenEvictionRunsMillis
         */
        public long timeBetweenEvictionRunsMillis = GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
        /**
         * @see GenericObjectPool#setNumTestsPerEvictionRun
         */
        public int numTestsPerEvictionRun =  GenericObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
        /**
         * @see GenericObjectPool#setMinEvictableIdleTimeMillis
         */
        public long minEvictableIdleTimeMillis = GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
        /**
         * @see GenericObjectPool#setSoftMinEvictableIdleTimeMillis
         */
        public long softMinEvictableIdleTimeMillis = GenericObjectPool.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
        /**
         * @see GenericObjectPool#setLifo
         */
        public boolean lifo = GenericObjectPool.DEFAULT_LIFO;
        //CHECKSTYLE: resume VisibilityModifier
    }

    /**
     * Latch used to control allocation order of objects to threads to ensure
     * fairness. That is, objects are allocated to threads in the order that
     * threads request objects.
     */
    private static final class Latch<T> {
        
        /** object timestamp pair allocated to this latch */
        private ObjectTimestampPair<T> _pair;
        
        /** Whether or not this latch may create an object instance */
        private boolean _mayCreate = false;

        /**
         * Returns ObjectTimestampPair allocated to this latch
         * @return ObjectTimestampPair allocated to this latch
         */
        private synchronized ObjectTimestampPair<T> getPair() {
            return _pair;
        }
        
        /**
         * Sets ObjectTimestampPair on this latch
         * @param pair ObjectTimestampPair allocated to this latch
         */
        private synchronized void setPair(ObjectTimestampPair<T> pair) {
            _pair = pair;
        }

        /**
         * Whether or not this latch may create an object instance 
         * @return true if this latch has an instance creation permit
         */
        private synchronized boolean mayCreate() {
            return _mayCreate;
        }
        
        /**
         * Sets the mayCreate property
         * @param mayCreate new value for mayCreate
         */
        private synchronized void setMayCreate(boolean mayCreate) {
            _mayCreate = mayCreate;
        }

        /**
         * Reset the latch data. Used when an allocation fails and the latch
         * needs to be re-added to the queue.
         */
        private synchronized void reset() {
            _pair = null;
            _mayCreate = false;
        }
    }


    //--- private attributes ---------------------------------------

    /**
     * The cap on the number of idle instances in the pool.
     * 
     * 池中最大空闲实例个数
     * (向pool中添加实例或者将实例放回pool中时会用到)
     * 
     * @see #setMaxIdle
     * @see #getMaxIdle
     */
    private int _maxIdle = DEFAULT_MAX_IDLE;

    /**
    * The cap on the minimum number of idle instances in the pool.
    * 
    * 池中最小空闲实例个数
    * (evictor会用到)
    * 
    * @see #setMinIdle
    * @see #getMinIdle
    */
    private int _minIdle = DEFAULT_MIN_IDLE;

    /**
     * The cap on the total number of active instances from the pool.
     * 
     * 最多活跃的实例个数
     * 则代表对最大活跃对象没有限制
     * 
     * @see #setMaxActive
     * @see #getMaxActive
     */
    private int _maxActive = DEFAULT_MAX_ACTIVE;

    /**
     * The maximum amount of time (in millis) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * 当池耗尽并且whenExhaustedAction的值是WHEN_EXHAUSTED_BLOCK时候,
     * borrowObject方法最大等待时间(毫秒),超过改时间则抛出异常
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     * 
     * 当小于等于0时，一直阻塞
     * 
     *
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private long _maxWait = DEFAULT_MAX_WAIT;

    /**
     * The action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     * 
     * 当池被耗尽时，borrowObject方法应该采取的措施
     *
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #DEFAULT_WHEN_EXHAUSTED_ACTION
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private byte _whenExhaustedAction = DEFAULT_WHEN_EXHAUSTED_ACTION;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     * 
     * 默认是false，当是true得时候，borrowObject方法在返回之前
     * 会调用自定义的PoolableObjectFactory.validateObject方法，校验
     * 要返回的对象是否可用.如果不可用，该对象会被销毁，然后
     * 会尝试生成另一个对象.
     *
     * @see #setTestOnBorrow
     * @see #getTestOnBorrow
     */
    private volatile boolean _testOnBorrow = DEFAULT_TEST_ON_BORROW;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     * 
     * 默认是false，当时true的时候，当调用retruenObject将对象放回
     * 池中时会调用PoolableObjectFactory.validateObject方法，校验对象
     * 是否可用.
     * (如果不可用会销毁该对象)
     * 
     *
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    private volatile boolean _testOnReturn = DEFAULT_TEST_ON_RETURN;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * 默认是fasle，当是true时，闲置对象驱逐器会调用validateObject
     * 方法，校验对象是否可用，如果不可用会将其从池中drop掉
     *
     * @see #setTestWhileIdle
     * @see #getTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private boolean _testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    /**
     * The number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * 闲置对象驱逐器运行的间隔时间(毫秒)，如果是非正数
     * 则驱逐器将不会运行.
     * (驱逐器可以确保池内对象不小于minIdle并且不大于maxIdle)
     *
     * @see #setTimeBetweenEvictionRunsMillis
     * @see #getTimeBetweenEvictionRunsMillis
     */
    private long _timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * 
     * 驱逐器每次运行时检查池中闲置对象的最大个数
     * (比如该值设置为3,此时池中有5个闲置对象,那么每次
     * 只会检查前三个闲置对象。比如检查闲置对象是否可用等。
     * 确保minIdle不受这个影响，因为是做完检查后才执行确保代码)
     * 
     * <p>
     * When a negative value is supplied, <tt>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @see #setNumTestsPerEvictionRun
     * @see #getNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private int _numTestsPerEvictionRun =  DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * The minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     * 
     * 一个对象可以停留在池中的最少闲置时间，如果该对象在池中
     * 的闲置时间大于该值，那么该对象就可以被驱逐器dorp掉。
     * 
     * 如果是非正数，表示该对象可以一直闲置下去。
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #getMinEvictableIdleTimeMillis
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private long _minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * The minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" amount of object remain in the pool.
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     * 
     * 和minEvictableIdleTimeMillis作用一样，但有一个额外条件：
     * 如果池中闲置对象的个数不大于minIdle时，即使有对象的
     * 闲置时间大于该设置的值也不会被dorp掉.
     * 
     * 注意：如果minEvictableIdleTimeMillis>0则该参数就不在起作用.
     *
     * @see #setSoftMinEvictableIdleTimeMillis
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    private long _softMinEvictableIdleTimeMillis = DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /** Whether or not the pool behaves as a LIFO queue (last in first out) */
    private boolean _lifo = DEFAULT_LIFO;

    /** My pool. */
    private CursorableLinkedList<ObjectTimestampPair<T>> _pool = null;

    /** Eviction cursor - keeps track of idle object evictor position */
    private CursorableLinkedList<ObjectTimestampPair<T>>.Cursor _evictionCursor = null;

    /** My {@link PoolableObjectFactory}. */
    private PoolableObjectFactory<T> _factory = null;

    /**
     * The number of objects {@link #borrowObject} borrowed
     * from the pool, but not yet returned.
     */
    private int _numActive = 0;

    /**
     * My idle object eviction {@link TimerTask}, if any.
     */
    private Evictor _evictor = null;

    /**
     * The number of objects subject to some form of internal processing
     * (usually creation or destruction) that should be included in the total
     * number of objects but are neither active nor idle.
     * 这是一个内部正在处理的数字，表示池中对象已经分配给请求(latch),
     * 但还没有被实际用到的实例的个数(既不是active对象也不是idle对象)
     * 可用请求(已分配池对象或_mayCreate标记为true的latch)个数
     */
    private int _numInternalProcessing = 0;

    /**
     * Used to track the order in which threads call {@link #borrowObject()} so
     * that objects can be allocated in the order in which the threads requested
     * them.
     */
    private final LinkedList<Latch<T>> _allocationQueue = new LinkedList<Latch<T>>();

}
