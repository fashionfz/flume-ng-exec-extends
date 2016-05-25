# flume-ng-exec-extends

log4j的flume exec插件，根据正则表达式采集完整日志作为一个event

使用

指定入口

a1.sources.s.type = com.chinawiserv.flume.log4j.source.Log4jExecSource

a1.sources.s.channels = c

a1.sources.s.command = tail -F /home/hadoop/dataAnalyse-web-1.0.0-bin/logs/root.log

指定正则表达式，如果满足表达是即为一条日志的开始

a1.sources.s.datePattern = ^\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}