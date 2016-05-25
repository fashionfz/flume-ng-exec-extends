/**
 * 工程名称:flume-ng-exec-extends
 * 文件名称:Log4jExecSource.java
 * 包名称:com.chinawiserv.flume.log4j.source
 * 日期:2016年5月25日上午9:27:03
 * 版权所有: 版权所有(C)2013-2014
 */
package com.chinawiserv.flume.log4j.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.SystemClock;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.source.AbstractSource;
import org.apache.flume.source.ExecSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * <li>文件名称: flume-ng-exec-extends</li>
 * <li>文件描述: Log4jExecSource.java</li>
 * <li>版权所有: 版权所有(C)2016-2018</li>
 * <li>公 司: 勤智数码科技股份有限公司</li>
 * <li>内容摘要:描述类是做什么的，有哪些功能</li>
 * <li>其他说明:无</li>
 * <li>完成日期：2016年5月25日</li>
 * <li>修改记录: 无</li>
 * 
 * @version 产品版本
 * @author Administrator
 */
public class Log4jExecSource extends AbstractSource implements EventDrivenSource, Configurable {

	private static final Logger logger = LoggerFactory.getLogger(ExecSource.class);

	private String shell;
	private String command;
	private SourceCounter sourceCounter;
	private ExecutorService executor;
	private Future<?> runnerFuture;
	private long restartThrottle;
	private boolean restart;
	private boolean logStderr;
	private Integer bufferCount;
	private long batchTimeout;
	private ExecRunnable runner;
	private Charset charset;
	private String datePattern;

	@Override
	public void start() {
		logger.info("Exec source starting with command:{}", command);

		executor = Executors.newSingleThreadExecutor();

		runner = new ExecRunnable(shell, command, getChannelProcessor(), sourceCounter, restart, restartThrottle,
				logStderr, bufferCount, batchTimeout, charset, datePattern);

		// FIXME: Use a callback-like executor / future to signal us upon
		// failure.
		runnerFuture = executor.submit(runner);

		/*
		 * NB: This comes at the end rather than the beginning of the method
		 * because it sets our state to running. We want to make sure the
		 * executor is alive and well first.
		 */
		sourceCounter.start();
		super.start();

		logger.debug("Exec source started");
	}

	@Override
	public void stop() {
		logger.info("Stopping exec source with command:{}", command);
		if (runner != null) {
			runner.setRestart(false);
			runner.kill();
		}

		if (runnerFuture != null) {
			logger.debug("Stopping exec runner");
			runnerFuture.cancel(true);
			logger.debug("Exec runner stopped");
		}
		executor.shutdown();

		while (!executor.isTerminated()) {
			logger.debug("Waiting for exec executor service to stop");
			try {
				executor.awaitTermination(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				logger.debug("Interrupted while waiting for exec executor service " + "to stop. Just exiting.");
				Thread.currentThread().interrupt();
			}
		}

		sourceCounter.stop();
		super.stop();

		logger.debug("Exec source with command:{} stopped. Metrics:{}", command, sourceCounter);
	}

	@Override
	public void configure(Context context) {
		command = context.getString("command");

		Preconditions.checkState(command != null, "The parameter command must be specified");

		restartThrottle = context.getLong(Log4jExecSourceConfigurationConstants.CONFIG_RESTART_THROTTLE,
				Log4jExecSourceConfigurationConstants.DEFAULT_RESTART_THROTTLE);

		restart = context.getBoolean(Log4jExecSourceConfigurationConstants.CONFIG_RESTART,
				Log4jExecSourceConfigurationConstants.DEFAULT_RESTART);

		logStderr = context.getBoolean(Log4jExecSourceConfigurationConstants.CONFIG_LOG_STDERR,
				Log4jExecSourceConfigurationConstants.DEFAULT_LOG_STDERR);

		bufferCount = context.getInteger(Log4jExecSourceConfigurationConstants.CONFIG_BATCH_SIZE,
				Log4jExecSourceConfigurationConstants.DEFAULT_BATCH_SIZE);

		batchTimeout = context.getLong(Log4jExecSourceConfigurationConstants.CONFIG_BATCH_TIME_OUT,
				Log4jExecSourceConfigurationConstants.DEFAULT_BATCH_TIME_OUT);

		charset = Charset.forName(context.getString(Log4jExecSourceConfigurationConstants.CHARSET,
				Log4jExecSourceConfigurationConstants.DEFAULT_CHARSET));

		shell = context.getString(Log4jExecSourceConfigurationConstants.CONFIG_SHELL, null);
		
		datePattern = context.getString(Log4jExecSourceConfigurationConstants.DATE_PATTERN, null);

		if (sourceCounter == null) {
			sourceCounter = new SourceCounter(getName());
		}
	}

	private static class ExecRunnable implements Runnable {
		//5M
		private static final int WRITE_EVENT_MAX = 5*1024*1024;

		public ExecRunnable(String shell, String command, ChannelProcessor channelProcessor,
				SourceCounter sourceCounter, boolean restart, long restartThrottle, boolean logStderr, int bufferCount,
				long batchTimeout, Charset charset, String datePattern) {
			this.command = command;
			this.channelProcessor = channelProcessor;
			this.sourceCounter = sourceCounter;
			this.restartThrottle = restartThrottle;
			this.bufferCount = bufferCount;
			this.batchTimeout = batchTimeout;
			this.restart = restart;
			this.logStderr = logStderr;
			this.charset = charset;
			this.shell = shell;
			this.datePattern = datePattern;
		}

		private final String shell;
		private final String command;
		private final ChannelProcessor channelProcessor;
		private final SourceCounter sourceCounter;
		private volatile boolean restart;
		private final long restartThrottle;
		private final int bufferCount;
		private long batchTimeout;
		private final boolean logStderr;
		private final Charset charset;
		private Process process = null;
		private SystemClock systemClock = new SystemClock();
		private Long lastPushToChannel = systemClock.currentTimeMillis();
		ScheduledExecutorService timedFlushService;
		ScheduledFuture<?> future;
		private final String datePattern;

		@Override
		public void run() {
			do {
				String exitCode = "unknown";
				BufferedReader reader = null;
				String line = null;
				final List<Event> eventList = new ArrayList<Event>();
				final StringBuffer event1 = new StringBuffer();
				final StringBuffer event2 = new StringBuffer();
				StringBuffer write = event1;
				StringBuffer read = event2;
				
				Pattern p = Pattern.compile(datePattern);
				Matcher m;

				timedFlushService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
						.setNameFormat("timedFlushExecService" + Thread.currentThread().getId() + "-%d").build());
				try {
					if (shell != null) {
						String[] commandArgs = formulateShellCommand(shell, command);
						process = Runtime.getRuntime().exec(commandArgs);
					} else {
						String[] commandArgs = command.split("\\s+");
						process = new ProcessBuilder(commandArgs).start();
					}
					reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));

					// StderrLogger dies as soon as the input stream is invalid
					StderrReader stderrReader = new StderrReader(
							new BufferedReader(new InputStreamReader(process.getErrorStream(), charset)), logStderr);
					stderrReader.setName("StderrReader-[" + command + "]");
					stderrReader.setDaemon(true);
					stderrReader.start();

					future = timedFlushService.scheduleWithFixedDelay(new Runnable() {
						@Override
						public void run() {
							try {
								synchronized (eventList) {
									if (!eventList.isEmpty() && timeout()) {
										flushEventBatch(eventList);
									}
								}
							} catch (Exception e) {
								logger.error("Exception occured when processing event batch", e);
								if (e instanceof InterruptedException) {
									Thread.currentThread().interrupt();
								}
							}
						}
					}, batchTimeout, batchTimeout, TimeUnit.MILLISECONDS);

					while ((line = reader.readLine()) != null) {
						m = p.matcher(line);
						synchronized (write) {
							//找到开始位置就交换
							if(write.length() > WRITE_EVENT_MAX || m.find()){
								if(write == event1){
									write = event2;
									read = event1;
								}else{
									write = event1;
									read = event2;
								}
								write.append(line);
								if(read.length() > 0){
									synchronized (eventList) {
										sourceCounter.incrementEventReceivedCount();
										eventList.add(EventBuilder.withBody(read.toString().getBytes(charset)));
										if (eventList.size() >= bufferCount || timeout()) {
											flushEventBatch(eventList);
										}
									}
									read.setLength(0);
								}
							}else{
								write.append("\n"+line);
							}
						}
					}

					synchronized (eventList) {
						if(write.length() > 0){
							eventList.add(EventBuilder.withBody(write.toString().getBytes(charset)));
							write.setLength(0);
						}
						if (!eventList.isEmpty()) {
							flushEventBatch(eventList);
						}
					}
				} catch (Exception e) {
					logger.error("Failed while running command: " + command, e);
					if (e instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
				} finally {
					if (reader != null) {
						try {
							reader.close();
						} catch (IOException ex) {
							logger.error("Failed to close reader for exec source", ex);
						}
					}
					exitCode = String.valueOf(kill());
				}
				if (restart) {
					logger.info("Restarting in {}ms, exit code {}", restartThrottle, exitCode);
					try {
						Thread.sleep(restartThrottle);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else {
					logger.info("Command [" + command + "] exited with " + exitCode);
				}
			} while (restart);
		}
		

		private void flushEventBatch(List<Event> eventList) {
			channelProcessor.processEventBatch(eventList);
			sourceCounter.addToEventAcceptedCount(eventList.size());
			eventList.clear();
			lastPushToChannel = systemClock.currentTimeMillis();
		}

		private boolean timeout() {
			return (systemClock.currentTimeMillis() - lastPushToChannel) >= batchTimeout;
		}

		private static String[] formulateShellCommand(String shell, String command) {
			String[] shellArgs = shell.split("\\s+");
			String[] result = new String[shellArgs.length + 1];
			System.arraycopy(shellArgs, 0, result, 0, shellArgs.length);
			result[shellArgs.length] = command;
			return result;
		}

		public int kill() {
			if (process != null) {
				synchronized (process) {
					process.destroy();

					try {
						int exitValue = process.waitFor();

						// Stop the Thread that flushes periodically
						if (future != null) {
							future.cancel(true);
						}

						if (timedFlushService != null) {
							timedFlushService.shutdown();
							while (!timedFlushService.isTerminated()) {
								try {
									timedFlushService.awaitTermination(500, TimeUnit.MILLISECONDS);
								} catch (InterruptedException e) {
									logger.debug("Interrupted while waiting for exec executor service "
											+ "to stop. Just exiting.");
									Thread.currentThread().interrupt();
								}
							}
						}
						return exitValue;
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				return Integer.MIN_VALUE;
			}
			return Integer.MIN_VALUE / 2;
		}

		public void setRestart(boolean restart) {
			this.restart = restart;
		}
	}

	private static class StderrReader extends Thread {
		private BufferedReader input;
		private boolean logStderr;

		protected StderrReader(BufferedReader input, boolean logStderr) {
			this.input = input;
			this.logStderr = logStderr;
		}

		@Override
		public void run() {
			try {
				int i = 0;
				String line = null;
				while ((line = input.readLine()) != null) {
					if (logStderr) {
						// There is no need to read 'line' with a charset
						// as we do not to propagate it.
						// It is in UTF-16 and would be printed in UTF-8 format.
						logger.info("StderrLogger[{}] = '{}'", ++i, line);
					}
				}
			} catch (IOException e) {
				logger.info("StderrLogger exiting", e);
			} finally {
				try {
					if (input != null) {
						input.close();
					}
				} catch (IOException ex) {
					logger.error("Failed to close stderr reader for exec source", ex);
				}
			}
		}
	}
}
