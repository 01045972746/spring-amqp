package org.springframework.amqp.rabbit.listener;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.rabbit.test.BrokerRunning;

@RunWith(Parameterized.class)
public class MessageListenerContainerLifecycleIntegrationTests {

	private static Log logger = LogFactory.getLog(MessageListenerContainerLifecycleIntegrationTests.class);

	private static Queue queue = new Queue("test.queue");

	private enum TransactionMode {
		ON, OFF;
		public boolean isTransactional() {
			return this == ON;
		}
	}

	private enum Concurrency {
		LOW(1);
		private final int value;

		private Concurrency(int value) {
			this.value = value;
		}

		public int value() {
			return this.value;
		}
	}

	private enum MessageCount {
		LOW(1), HIGH(200);
		private final int value;

		private MessageCount(int value) {
			this.value = value;
		}

		public int value() {
			return this.value;
		}
	}

	private RabbitTemplate template = new RabbitTemplate();

	private final int concurrentConsumers;

	private final boolean transactional;

	@Rule
	public BrokerRunning brokerIsRunning = BrokerRunning.isRunningWithEmptyQueue(queue);

	private final int messageCount;

	public MessageListenerContainerLifecycleIntegrationTests(MessageCount messageCount, Concurrency concurrency,
			TransactionMode transacted) {
		this.messageCount = messageCount.value();
		this.concurrentConsumers = concurrency.value();
		this.transactional = transacted.isTransactional();
	}

	@Parameters
	public static List<Object[]> getParameters() {
		@SuppressWarnings("unused")
		Object[] debug = new Object[] { MessageCount.LOW, Concurrency.LOW, TransactionMode.OFF };
		// return Collections.singletonList(debug);
		return Arrays.asList( //
				new Object[] { MessageCount.HIGH, Concurrency.LOW, TransactionMode.ON }, //
				new Object[] { MessageCount.HIGH, Concurrency.LOW, TransactionMode.OFF });
	}

	@Before
	public void declareQueue() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
		connectionFactory.setChannelCacheSize(concurrentConsumers);
		// connectionFactory.setPort(5673);
		template.setConnectionFactory(connectionFactory);
	}

	@Test
	public void testListenerSunnyDay() throws Exception {
		CountDownLatch latch = new CountDownLatch(messageCount);
		for (int i = 0; i < messageCount; i++) {
			template.convertAndSend(queue.getName(), i + "foo");
		}
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(template.getConnectionFactory());
		container.setMessageListener(new MessageListenerAdapter(new PojoListener(latch)));
		container.setChannelTransacted(transactional);
		container.setConcurrentConsumers(concurrentConsumers);
		container.setQueueName(queue.getName());
		container.afterPropertiesSet();
		container.start();
		try {
			boolean waited = latch.await(50, TimeUnit.MILLISECONDS);
			assertFalse("Expected time out waiting for message", waited);
			container.stop();
			Thread.sleep(500L);
			container.start();
			if (transactional) {
				waited = latch.await(5, TimeUnit.SECONDS);
				assertTrue("Timed out waiting for message", waited);
			}
			else {
				waited = latch.await(500, TimeUnit.MILLISECONDS);
				// If non-transactional we half expect to lose messages
				assertFalse("Expected time out waiting for message", waited);
			}
		}
		finally {
			// Wait for broker communication to finish before trying to stop
			// container
			Thread.sleep(300L);
			container.shutdown();
		}
		assertNull(template.receiveAndConvert(queue.getName()));
	}

	public static class PojoListener {
		private AtomicInteger count = new AtomicInteger();

		private final CountDownLatch latch;

		private final boolean fail;

		public PojoListener(CountDownLatch latch) {
			this(latch, false);
		}

		public PojoListener(CountDownLatch latch, boolean fail) {
			this.latch = latch;
			this.fail = fail;
		}

		public void handleMessage(String value) {
			try {
				logger.debug(value + count.getAndIncrement());
				if (fail) {
					throw new RuntimeException("Planned failure");
				}
			}
			finally {
				latch.countDown();
			}
		}
	}

}
