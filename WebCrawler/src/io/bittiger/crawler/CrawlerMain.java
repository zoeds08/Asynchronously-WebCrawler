package io.bittiger.crawler;

/**
 * Created by john on 10/12/16.
 */
import java.io.*;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.rabbitmq.client.*;
import io.bittiger.ad.Ad;

public class CrawlerMain {
    private final static String IN_QUEUE_NAME = "q_feeds";
    private final static String OUT_QUEUE_NAME = "q_product";
    private final static String ERR_QUEUE_NAME = "q_error";

    private static AmazonCrawler crawler;
    private static ObjectMapper mapper;

    private static Channel outChannel;
    private static Channel errChannel;


    public static void main(String[] args) throws IOException, TimeoutException, InterruptedIOException {
        if(args.length < 1)
        {
            System.out.println("Usage: Crawler <proxyFilePath> ");
            System.exit(0);
        }
        mapper = new ObjectMapper();
        String proxyFilePath = args[0];

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        com.rabbitmq.client.Channel inChannel = connection.createChannel();
        inChannel.queueDeclare(IN_QUEUE_NAME,true,false,false,null);
        inChannel.basicQos(10);
        System.out.println("[*] Waiting for messages. To exit press CTRL+C");


        outChannel= connection.createChannel();
        outChannel.queueDeclare(OUT_QUEUE_NAME,true,false,false,null);

        errChannel = connection.createChannel();
        errChannel.queueDeclare(ERR_QUEUE_NAME,true,false,false,null);

        crawler = new AmazonCrawler(proxyFilePath, errChannel, ERR_QUEUE_NAME);


        Consumer consumer = new DefaultConsumer(inChannel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                throws  IOException{
                try{
                    String message = new String(body,"UTF-8");
                    System.out.println(" [x] Received '" + message + "'");
                    String[] fields = message.split(",");
                    String query = fields[0].trim();
                    double bidPrice = Double.parseDouble(fields[1].trim());
                    int campaignId = Integer.parseInt(fields[2].trim());
                    int queryGroupId = Integer.parseInt(fields[3].trim());

                    List<Ad> ads =  crawler.GetAdBasicInfoByQuery(query, bidPrice, campaignId, queryGroupId);
                    for(Ad ad : ads) {
                        String jsonInString = mapper.writeValueAsString(ad);
                        System.out.println(jsonInString);
                        outChannel.basicPublish("", OUT_QUEUE_NAME,null,jsonInString.getBytes("UTF-8"));
                    }
                    Thread.sleep(2000);
                }catch (InterruptedException ex){
                    Thread.currentThread().interrupt();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        };
        inChannel.basicConsume(IN_QUEUE_NAME, true, consumer);
    }
}
