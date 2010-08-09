/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.amqp.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic builder class to create bindings for a more fluent API style in code based configuration.
 * 
 * @author Mark Pollack
 * @author Mark Fisher
 */
public final class BindingBuilder  {

	public static ExchangeConfigurer from(Queue queue) {
		return new ExchangeConfigurer(queue);
	}


	public static class ExchangeConfigurer {

		private final Queue queue;

		private ExchangeConfigurer(Queue queue) {
			this.queue = queue;
		}

		public Binding to(FanoutExchange exchange) {
			return new Binding(this.queue, exchange);
		}

		public HeadersExchangeMapConfigurer to(HeadersExchange exchange) {
			return new HeadersExchangeMapConfigurer(this.queue, exchange);
		}

		public DirectExchangeRoutingKeyConfigurer to(DirectExchange exchange) {
			return new DirectExchangeRoutingKeyConfigurer(this.queue, exchange);
		}

		public TopicExchangeRoutingKeyConfigurer to(TopicExchange exchange) {
			return new TopicExchangeRoutingKeyConfigurer(this.queue, exchange);
		}
	}


	public static class HeadersExchangeMapConfigurer {

		protected final Queue queue;

		protected final HeadersExchange exchange;

		private HeadersExchangeMapConfigurer(Queue queue, HeadersExchange exchange) {
			this.queue = queue;
			this.exchange = exchange;
		}

		public Binding matchingAny(String... headerKeys) {
			Map<String, Object> arguments = new HashMap<String, Object>();
			for (String key : headerKeys) {
				arguments.put(key, null);
			}
			arguments.put("x-match", "any");
			return new Binding(queue, exchange, arguments);
		}

		public Binding matchingAny(Map<String, Object> headerValues) {
			Map<String, Object> arguments = new HashMap<String, Object>(headerValues);
			arguments.put("x-match", "any");
			return new Binding(queue, exchange, arguments);
		}

		public Binding matchingAll(String... headerKeys) {
			Map<String, Object> arguments = new HashMap<String, Object>();
			for (String key : headerKeys) {
				arguments.put(key, null);
			}
			arguments.put("x-match", "all");
			return new Binding(queue, exchange, arguments);
		}

		public Binding matchingAll(Map<String, Object> headerValues) {
			Map<String, Object> arguments = new HashMap<String, Object>(headerValues);
			arguments.put("x-match", "all");
			return new Binding(queue, exchange, arguments);
		}
	}


	private static abstract class AbstractRoutingKeyConfigurer<E extends Exchange> {

		protected final Queue queue;

		protected final E exchange;

		private AbstractRoutingKeyConfigurer(Queue queue, E exchange) {
			this.queue = queue;
			this.exchange = exchange;
		}
	}


	public static class TopicExchangeRoutingKeyConfigurer extends AbstractRoutingKeyConfigurer<TopicExchange> {

		private TopicExchangeRoutingKeyConfigurer(Queue queue, TopicExchange exchange) {
			super(queue, exchange);
		}

		public Binding with(String routingKey) {
			return new Binding(this.queue, this.exchange, routingKey);
		}

		public Binding with(Enum<?> routingKeyEnum) {
			return new Binding(this.queue, this.exchange, routingKeyEnum.toString());
		}
	}


	public static class DirectExchangeRoutingKeyConfigurer extends AbstractRoutingKeyConfigurer<DirectExchange> {

		private DirectExchangeRoutingKeyConfigurer(Queue queue, DirectExchange exchange) {
			super(queue, exchange);
		}

		public Binding with(String routingKey) {
			return new Binding(this.queue, this.exchange, routingKey);
		}

		public Binding with(Enum<?> routingKeyEnum) {
			return new Binding(this.queue, this.exchange, routingKeyEnum.toString());
		}

		public Binding withQueueName() {
			return new Binding(this.queue, this.exchange, this.queue.getName());
		}
	}

}
