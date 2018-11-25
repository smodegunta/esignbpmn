package com.brimma.bpm.config;

import com.docusign.esign.client.ApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import static org.springframework.boot.autoconfigure.jms.JmsProperties.AcknowledgeMode.CLIENT;

@EnableRetry
@Configuration
@EnableTransactionManagement
/**
 * Application configuration class which is a substitute for the bean xml
 */
public class AppConfig {
    //mq details

    @Value("${mq.brokerUrl}")
    private String brokerUrl;

    @Value("${mq.username}")
    private String username;

    @Value("${mq.password}")
    private String password;

    @Value("${mq.redelivery.delay}")
    private long delay;

    @Value("${mq.redelivery.maxRedeliveries}")
    private int maxRedeliveries;

    @Value("${mq.redelivery.backOffMultiplier}")
    private int backOffMultiplier;

    @Value("${mq.redelivery.enableExpoBackOff}")
    private boolean enableExpoBackOff;

    @Value("${mq.message.ttl}")
    private long ttlExpire;

    @Value("${pool.size:-1}")
    private int threadPoolSize;

    @Bean
    public ApiClient apiClient() {
        return new ApiClient();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObjectMapper objectMapper(){
        return new ObjectMapper();
    }

    @Bean
    public RetryTemplate retryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxRedeliveries);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(delay);
        backOffPolicy.setMultiplier(backOffMultiplier);

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }

    @Bean
    public ActiveMQConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(brokerUrl);
        factory.setUserName(username);
        factory.setPassword(password);
        factory.setUseAsyncSend(true);
        //setting redelivery policies
        RedeliveryPolicy policy = factory.getRedeliveryPolicy();
        policy.setInitialRedeliveryDelay(delay);
        policy.setBackOffMultiplier(backOffMultiplier);
        policy.setUseExponentialBackOff(enableExpoBackOff);
        policy.setMaximumRedeliveries(maxRedeliveries);
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate() {
        JmsTemplate template = new JmsTemplate();
        template.setDeliveryPersistent(true);
        template.setTimeToLive(ttlExpire);
        template.setConnectionFactory(connectionFactory());
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory());
        factory.setConcurrency("1-1");
        factory.setSessionAcknowledgeMode(CLIENT.getMode());
        return factory;
    }

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                threadPoolSize>0?threadPoolSize:Runtime.getRuntime().availableProcessors()*2
        );
        executor.setThreadFactory(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread workerThread = new Thread(r);
                //TODO: threads exception handler needs to be injected
                workerThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        System.out.println("Thread "+t+" has exception which is not handled");
                        e.printStackTrace();
                    }
                });
                return workerThread;
            }
        });
        return executor;
    }
}
