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

package org.springframework.amqp.rabbit.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.otp.erlang.connection.SimpleConnectionFactory;
import org.springframework.otp.erlang.core.ErlangTemplate;
import org.springframework.util.exec.Execute;
import org.springframework.util.exec.Os;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP.Exchange.DeleteOk;

/**
 * Rabbit broker administration implementation exposed via JMX annotations. 
 * @author Mark Pollack
 *
 */
public class RabbitBrokerAdmin implements RabbitBrokerOperations {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());
	
	private RabbitTemplate rabbitTemplate;
	
	private RabbitAdmin rabbitAdmin;
	
	private ErlangTemplate erlangTemplate;
	
	private String virtualHost;
	
	public RabbitBrokerAdmin(ConnectionFactory connectionFactory) {
		this.virtualHost = connectionFactory.getVirtualHost();
		this.rabbitTemplate = new RabbitTemplate(connectionFactory);
		this.rabbitAdmin = new RabbitAdmin(rabbitTemplate);
		initializeDefaultErlangTemplate(rabbitTemplate);		
	}
	
	
	// Exchange Operations

	@Override	
	public void declareExchange(AbstractExchange exchange) {
		rabbitAdmin.declareExchange(exchange);
	}

	/**
	 * Declare an exchange specifying its durability and auto-delete behavior.  Explicit arguments are given so as to 
	 * make this method easily accessible from JMX management consoles.
	 * Durable exchanges last until they are deleted, they will survive a server restart.
	 * Auto-deleted exchanges last until they are no longer used
	 * @param exchangeName the name of the exchange
	 * @param exchangeType the exchange type
	 * @param durable true if we are declaring a durable exchange (the exchange will survive a server restart)
	 * @param autoDelete true if the server should delete the exchange when it is no longer in use
	 */
	@ManagedOperation
	public void declareExchange(final String exchangeName, final String exchangeType, final boolean durable, final boolean autoDelete) {
		rabbitTemplate.execute(new ChannelCallback<Object>() {
			public Object doInRabbit(Channel channel) throws Exception {
				channel.exchangeDeclare(exchangeName, exchangeType, durable,
						autoDelete, new HashMap<String,Object>());
				return null;
			}
		});
	}
	
	@Override
	@ManagedOperation(description="Delete a exchange, without regard for whether it is in use or has messages on it")
	@ManagedOperationParameters(@ManagedOperationParameter(name = "exchange", description = "the name of the exchange"))
	public void deleteExchange(String exchangeName) {
		rabbitAdmin.deleteExchange(exchangeName);

	}
	
	@Override
	@ManagedOperation
	public DeleteOk deleteExchange(final String exchangeName, final boolean ifUnused) {
		return rabbitTemplate.execute(new ChannelCallback<DeleteOk>() {
			public DeleteOk doInRabbit(Channel channel) throws Exception {
				channel.exchangeDelete(exchangeName, ifUnused);
				return null;
			}
		});
	}
	

	// Queue Operations
	
	@Override
	@ManagedOperation
	public void declareQueue(Queue queue) {
		rabbitAdmin.declareQueue(queue);
	}

	@Override
	@ManagedOperation
	public void deleteQueue(String queueName) {
		rabbitAdmin.deleteQueue(queueName);
	}

	@Override
	@ManagedOperation
	public void deleteQueue(String queueName, boolean unused, boolean empty) {
		rabbitAdmin.deleteQueue(queueName, unused, empty);

	}

	@Override
	@ManagedOperation
	public void purgeQueue(String queueName, boolean noWait) {
		rabbitAdmin.purgeQueue(queueName, noWait);

	}
	
	public Map<String, String> getQueueInfo(String name) {
		// TODO: is there a more efficient way (direct RPC) to retrieve info for a single queue by name?
		Map<String, Map<String, String>> queues = this.getQueueInfo();
		return queues.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, Map<String, String>> getQueueInfo() { 
		return (Map<String, Map<String, String>>) erlangTemplate.executeAndConvertRpc("rabbit_amqqueue", "info_all", virtualHost.getBytes());
	}
	
	// Binding operations 
	@Override
	public void declareBinding(Binding binding) {
		rabbitAdmin.declareBinding(binding);	
	}


	@Override
	public void removeBinding(final Binding binding) {
		rabbitTemplate.execute(new ChannelCallback<Object>() {
			public Object doInRabbit(Channel channel) throws Exception {
				channel.queueUnbind(binding.getQueue(), binding.getExchange(), binding.getRoutingKey(), binding.getArguments());
				return null;
			}
		});
	}
	
	
	 // User management

	
	@Override
	@ManagedOperation()	
	public void addUser(String username, String password) {
		erlangTemplate.executeAndConvertRpc("rabbit_access_control", "add_user", username.getBytes(), password.getBytes());
	}

	@Override
	@ManagedOperation
	public void deleteUser(String username) {
		erlangTemplate.executeAndConvertRpc("rabbit_access_control", "delete_user", username.getBytes());	
	}


	@Override
	@ManagedOperation
	public void changeUserPassword(String username, String newPassword) {
		erlangTemplate.executeAndConvertRpc("rabbit_access_control", "change_password", username.getBytes(), newPassword.getBytes());		
	}


	@Override
	@SuppressWarnings("unchecked")	
	@ManagedOperation
	public List<String> listUsers() {
		return (List<String>) erlangTemplate.executeAndConvertRpc("rabbit_access_control", "list_users");	
	}


	@Override
	public int addVhost(String vhostPath) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int deleteVhost(String vhostPath) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setPermissions(String username, Pattern configure,
			Pattern read, Pattern write) {
		// TODO Auto-generated method stub
	
	}


	@Override
	public void setPermissions(String username, Pattern configure, Pattern read, Pattern write, String vhostPath) {
		// TODO Auto-generated method stub
	
	}


	@Override
	public void clearPermissions(String username) {
		// TODO Auto-generated method stub

	}

	@Override
	public void clearPermissions(String username, String vhostPath) {
		// TODO Auto-generated method stub

	}



	@Override
	public List<String> listPermissions() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public List<String> listPermissions(String vhostPath) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public List<String> listUserPermissions(String username) {
		// TODO Auto-generated method stub
		return null;
	}

	

	@Override
	@ManagedOperation
	public void startBrokerApplication() {
		logger.debug("Starting Rabbit Application.");
		erlangTemplate.executeAndConvertRpc("rabbit", "start");
	}


	@Override
	@ManagedOperation
	public void stopBrokerApplication() {
		logger.debug("Stopping Rabbit Application.");
		erlangTemplate.executeAndConvertRpc("rabbit", "stop");
	}


	@Override
	@ManagedOperation
	public void startNode() {
		logger.debug("Staring RabbitMQ node by shelling out command line.");
		final Execute execute = new Execute();

		String rabbitStartCommand = null;
		if (Os.isFamily("windows")) {
			String rabbitHome = System.getenv("RABBITMQ_HOME");
			// TODO remove any trailing directory separators on rabbit home var.
			if (rabbitHome == null) {
				throw new IllegalArgumentException(
						"RABBITMQ_HOME environment variable not set.");
			}
			rabbitStartCommand = rabbitHome
					+ System.getProperty("file.separator") + "sbin"
					+ System.getProperty("file.separator")
					+ "rabbitmq-server.bat";
		} else {
			// TODO abstract out install location and Win/Unix shell script name differencese
			throw new IllegalArgumentException("Only support for windows OS family at the moment...");
		}

		if (rabbitStartCommand != null) {
			execute.setCommandline(new String[] { rabbitStartCommand });
		} else {
			throw new IllegalArgumentException(
					"Could determine OS to create rabbit start command");
		}

		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
		executor.execute(new Runnable() {
			public void run() {
				try {
					execute.execute();
				} catch (Exception e) {
					logger.error("Could start node", e);
				}
			}
		});
	}


	@Override
	@ManagedOperation
	public void stopNode() {
		logger.debug("Stopping RabbitMQ node.");
		erlangTemplate.executeAndConvertRpc("rabbit", "stop_and_halt");
	}


	@Override
	@ManagedOperation
	public void resetNode() {
		erlangTemplate.executeAndConvertRpc("rabbit_mnesia", "reset");
	}


	@Override
	@ManagedOperation
	public void forceResetNode() {
		erlangTemplate.executeAndConvertRpc("rabbit_mnesia", "force_reset");

	}

	@Override
	@ManagedOperation
	public RabbitStatus getStatus() {
		return (RabbitStatus) getErlangTemplate().executeAndConvertRpc("rabbit", "status");
	}

	@Override
	public void recoverAsync(boolean requeue) {
		// TODO Auto-generated method stub

	}

	public ErlangTemplate getErlangTemplate() {
		return this.erlangTemplate;
	}	
	
	protected void initializeDefaultErlangTemplate(RabbitTemplate rabbitTemplate) {	
		String peerNodeName = "rabbit@" + rabbitTemplate.getConnectionFactory().getHostName();
		logger.debug("Creating jinterface connection with peerNodeName = [" + peerNodeName);
		SimpleConnectionFactory otpCf = new SimpleConnectionFactory("rabbit-spring-monitor", peerNodeName);
		otpCf.afterPropertiesSet();
		createErlangTemplate(otpCf);
	}
	
	protected void createErlangTemplate(org.springframework.otp.erlang.connection.ConnectionFactory otpCf) {
		erlangTemplate = new ErlangTemplate(otpCf);
		erlangTemplate.setErlangConverter(new RabbitControlErlangConverter());
		erlangTemplate.afterPropertiesSet();
	}



}
