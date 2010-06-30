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

package org.springframework.amqp.rabbit.admin.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.admin.RabbitAdminTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;

/**
 * Abstract base class for code based configuration of Spring managed Rabbit broker infrastructure,
 * i.e. Queues, Exchanges, Bindings.
 * <p>Subclasses are required to provide an implementation of rabbitTemplate from which the the bean 
 * 'rabbitAdminTemplate' will be created.
 * <p>There are several convenience 'declare' methods to make the configuration in subclasses
 * more terse and readable.
 * <p>Builder classes to make the configuration more fluent are under development. Look in the 
 * StockApp sample for an example of a fluent API to declare a binding.
 *
 * @author Mark Pollack
 */
@Configuration
public abstract class AbstractRabbitConfiguration {

	@Bean 
	public abstract RabbitTemplate rabbitTemplate();

	@Bean
	public RabbitAdminTemplate rabbitAdminTemplate() {
		return new RabbitAdminTemplate(rabbitTemplate());
	}

	public Queue declareQueue() {
		DeclareOk result = rabbitAdminTemplate().declareQueue();
		Queue queue = new Queue(result.getQueue());
		queue.setExclusive(true);
		queue.setAutoDelete(true);
		queue.setDurable(false);
		return queue;
	}

	public DirectExchange defaultDirectExchange() {
		return new DirectExchange("");  // server already has declared it.
	}
	
	public Queue declare(Queue queue) {
		rabbitAdminTemplate().declareQueue(queue);			
		return queue;
	}

	public Binding declare(Binding binding) {
		rabbitAdminTemplate().declareBinding(binding);
		return binding;
	}
	
	/**
	 * Provides the same as {@link declare(Binding)} but is better to use in conjunction with
	 * BindingBuilder so the fluent API reads better.
	 * <p>
	 * For example, 
	 * "declareBinding(from(marketDataQueue()).to(marketDataExchange()).with(marketDataRoutingKey));" 
	 * instead of 
	 * "declare(from(marketDataQueue()).to(marketDataExchange()).with(marketDataRoutingKey));"
	 * @param binding
	 * @return
	 */
	public Binding declareBinding(Binding binding) {
		rabbitAdminTemplate().declareBinding(binding);
		return binding;
	}

	public DirectExchange declare(DirectExchange directExchange) {
		rabbitAdminTemplate().declareExchange(directExchange);
		return directExchange;
	}

	public TopicExchange declare(TopicExchange topicExchange) {
		rabbitAdminTemplate().declareExchange(topicExchange);
		return topicExchange;
	}

	public FanoutExchange declare(FanoutExchange fanoutExchange) {
		rabbitAdminTemplate().declareExchange(fanoutExchange);
		return fanoutExchange;
	}

}
