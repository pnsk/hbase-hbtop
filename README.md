# hbase-hbtop

Usage:
```
java -jar hbase-hbtop-1.1.2.2.6.5.0-292.jar -Dzookeeper.znode.parent=/hbase-unsecure
```

For secure clusters:
```
CLASSPATH=/etc/hbase/conf:/etc/hadoop/conf:hbase-hbtop-1.1.2.2.6.5.0-292.jar java org.apache.hadoop.hbase.hbtop.HBTop
```

See also:
- https://blogs.apache.org/hbase/entry/introduction-hbtop-a-real-time
- https://hbase.apache.org/book.html#hbtop
