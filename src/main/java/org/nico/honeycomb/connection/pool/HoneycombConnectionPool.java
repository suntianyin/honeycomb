package org.nico.honeycomb.connection.pool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.nico.honeycomb.connection.HoneycombConnection;
import org.nico.honeycomb.connection.pool.feature.HoneycombConnectionPoolCleaner;
import org.nico.honeycomb.connection.pool.feature.HoneycombConnectionPoolLRU;


public class HoneycombConnectionPool implements HoneycombConnectionPoolFeature{

    private int maxPoolSize;
    
    private long maxIdleTime;

    private HoneycombConnection[] pools;

    private AtomicInteger poolIndex;

    private ArrayBlockingQueue<HoneycombConnection> idleQueue;
    
    private ArrayBlockingQueue<HoneycombConnection> freezeQueue;
    
    private Thread cleaner;
    
    private Thread lru;

    public HoneycombConnectionPool(int maxPoolSize, long maxIdleTime) {
        pools = new HoneycombConnection[this.maxPoolSize = maxPoolSize];
        idleQueue = new ArrayBlockingQueue<HoneycombConnection>(maxPoolSize);
        freezeQueue = new ArrayBlockingQueue<HoneycombConnection>(maxPoolSize);
        poolIndex = new AtomicInteger(-1);
        cleaner = new HoneycombConnectionPoolCleaner(this, this.maxIdleTime = maxIdleTime, 1000 * 5l);
        lru = new HoneycombConnectionPoolLRU(this, 1000 * 5l);
    }

    public Integer applyIndex() {
        if(poolIndex.get() < maxPoolSize) {
            Integer index = poolIndex.incrementAndGet();
            if(index < maxPoolSize) {
                return index;
            }
        }
        return null;
    }

    public boolean assignable() {
        return ! idleQueue.isEmpty();
    }
    
    public boolean actionable() {
        return ! freezeQueue.isEmpty();
    }
    
    public HoneycombConnection getIdleConnection(long wait) {
        try {
            while(wait > 0) {
                long beginPollNanoTime = System.nanoTime();
                HoneycombConnection nc = idleQueue.poll(wait, TimeUnit.MILLISECONDS);
                if(nc != null) {
                    if(nc.isClosed() && nc.switchOccupied()) {
                        idleQueue.remove(nc);
                        return nc;
                    }
                }
                long timeConsuming = (System.nanoTime() - beginPollNanoTime) / 1000 * 1000 ;
                wait -= timeConsuming;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
        }
        throw new RuntimeException("获取连接超时");
    }
    
    public HoneycombConnection getFreezeConnection() {
        return freezeQueue.poll();
    }

    public HoneycombConnection putOccupiedConnection(HoneycombConnection nc, Integer id) {
        nc.switchOccupied();
        nc.setIndex(id);
        pools[id] = nc;
        idleQueue.remove(nc);
        return nc;
    }

    public HoneycombConnection putLeisureConnection(HoneycombConnection nc, Integer id) {
        nc.switchIdle();
        nc.setIndex(id);
        pools[id] = nc;
        idleQueue.add(nc);
        return nc;
    }

    public void recycle(HoneycombConnection nc) {
        idleQueue.add(nc);
    }

    public HoneycombConnection[] getPools() {
        return pools;
    }

    public void setPools(HoneycombConnection[] pools) {
        this.pools = pools;
    }

    public AtomicInteger getPoolIndex() {
        return poolIndex;
    }

    public void setPoolIndex(AtomicInteger poolIndex) {
        this.poolIndex = poolIndex;
    }
    
    @Override
    public void enableCleaner() {
        cleaner.start();
    }

    @Override
    public void enableMonitor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableLRU() {
        lru.start();
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public ArrayBlockingQueue<HoneycombConnection> getIdleQueue() {
        return idleQueue;
    }

    public void setIdleQueue(ArrayBlockingQueue<HoneycombConnection> idleQueue) {
        this.idleQueue = idleQueue;
    }

    public Thread getCleaner() {
        return cleaner;
    }

    public void setCleaner(Thread cleaner) {
        this.cleaner = cleaner;
    }

    public ArrayBlockingQueue<HoneycombConnection> getFreezeQueue() {
        return freezeQueue;
    }

    public void setFreezeQueue(ArrayBlockingQueue<HoneycombConnection> freezeQueue) {
        this.freezeQueue = freezeQueue;
    }
    
}
