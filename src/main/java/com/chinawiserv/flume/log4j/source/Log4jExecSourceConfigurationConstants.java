/**
 * 工程名称:flume-ng-exec-extends
 * 文件名称:Log4jExecSourceConfigurationConstants.java
 * 包名称:com.chinawiserv.flume.log4j.source
 * 日期:2016年5月25日上午9:28:44
 * 版权所有: 版权所有(C)2013-2014
 */
package com.chinawiserv.flume.log4j.source;

/**
 * <li>文件名称: flume-ng-exec-extends</li>
 * <li>文件描述: Log4jExecSourceConfigurationConstants.java</li>
 * <li>版权所有: 版权所有(C)2016-2018</li>
 * <li>公    司: 勤智数码科技股份有限公司</li>
 * <li>内容摘要:描述类是做什么的，有哪些功能</li>
 * <li>其他说明:无</li>
 * <li>完成日期：2016年5月25日</li>
 * <li>修改记录: 无</li>
 * @version 产品版本
 * @author Administrator
 */
public class Log4jExecSourceConfigurationConstants {

	  /**
	   * Should the exec'ed command restarted if it dies: : default false
	   */
	  public static final String CONFIG_RESTART = "restart";
	  public static final boolean DEFAULT_RESTART = false;

	  /**
	   * Amount of time to wait before attempting a restart: : default 10000 ms
	   */
	  public static final String CONFIG_RESTART_THROTTLE = "restartThrottle";
	  public static final long DEFAULT_RESTART_THROTTLE = 10000L;

	  /**
	   * Should stderr from the command be logged: default false
	   */
	  public static final String CONFIG_LOG_STDERR = "logStdErr";
	  public static final boolean DEFAULT_LOG_STDERR = false;

	  /**
	   * Number of lines to read at a time
	   */
	  public static final String CONFIG_BATCH_SIZE = "batchSize";
	  public static final int DEFAULT_BATCH_SIZE = 20;

	  /**
	   * Amount of time to wait, if the buffer size was not reached, before 
	   * to data is pushed downstream: : default 3000 ms
	   */
	  public static final String CONFIG_BATCH_TIME_OUT = "batchTimeout";
	  public static final long DEFAULT_BATCH_TIME_OUT = 3000l;

	  /**
	   * Charset for reading input
	   */
	  public static final String CHARSET = "charset";
	  public static final String DEFAULT_CHARSET = "UTF-8";

	  /**
	   * Optional shell/command processor used to run command
	   */
	  public static final String CONFIG_SHELL = "shell";
	  
	  public static final String DATE_PATTERN = "datePattern";
}
