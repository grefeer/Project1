package com.aiqa.project1.utils;

import io.lettuce.core.dynamic.annotation.Value;
import org.springframework.stereotype.Component;


public class SnowFlakeUtil {
   private static final long twepoch = 1288834974657L;
   private static final long workerIdBits = 5L;
   private static final long datacenterIdBits = 5L;
   private static final long maxWorkerId = -1L ^ (-1L << workerIdBits);
   private static final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
   private static final long sequenceBits = 12L;
   private static final long workerIdShift = sequenceBits;
   private static final long datacenterIdShift = sequenceBits + workerIdBits;
   private static final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
   private static final long sequenceMask = -1L ^ (-1L << sequenceBits);
   private long workerId;
   private long datacenterId;
   private long sequence = 0L;
   private long lastTimestamp = -1L;
   public SnowFlakeUtil(long workerId, long datacenterId) {
       if (workerId > maxWorkerId || workerId < 0) {
           throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
       }
       if (datacenterId > maxDatacenterId || datacenterId < 0) {
           throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
       }
       this.workerId = workerId;
       this.datacenterId = datacenterId;
   }
   public synchronized long nextId() {
       long timestamp = timeGen();
       if (timestamp < lastTimestamp) {
           throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
       }
       if (lastTimestamp == timestamp) {
           sequence = (sequence + 1) & sequenceMask;
           if (sequence == 0) {
               timestamp = tilNextMillis(lastTimestamp);
           }
       } else {
           sequence = 0L;
       }
       lastTimestamp = timestamp;
       return ((timestamp - twepoch) << timestampLeftShift) |
               (datacenterId << datacenterIdShift) |
               (workerId << workerIdShift) |
               sequence;
   }
   private long tilNextMillis(long lastTimestamp) {
       long timestamp = timeGen();
       while (timestamp <= lastTimestamp) {
           timestamp = timeGen();
       }
       return timestamp;
   }
   private long timeGen() {
       return System.currentTimeMillis();
   }
}