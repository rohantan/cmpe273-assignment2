package edu.sjsu.cmpe.procurement.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This job will run at every 5 minutes.
 */
@Every("300s")
public class ProcurementSchedulerJob extends Job {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void doJob() {
		String strResponse = ProcurementService.jerseyClient.resource(
				"http://ip.jsontest.com/").get(String.class);
		log.debug("Response from jsontest.com: {}", strResponse);

		ProcurementSchedulerJob object = new ProcurementSchedulerJob();

		String isbnList="";
		try {
			isbnList = object.consumer();
			System.out.println("consuming");
		} catch (JMSException e) {
			e.printStackTrace();
		}
		if(isbnList != "")
		{
			object.postToPublisher(isbnList);
			System.out.println("posting to publisher");
		}

		try {
			object.Publisher();
			System.out.println("consuming from publisher");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void Publisher() throws ParseException, JMSException
	{
		JSONParser parser = new JSONParser();

		try { URL url = new URL(" http://54.193.56.218:9000/orders/96933");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		if (conn.getResponseCode() != 200)
		{
			throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
		}

		BufferedReader br = new BufferedReader(new InputStreamReader( (conn.getInputStream())));

		String output;
		System.out.println("Output from Server .... \n");
		while ((output = br.readLine()) != null)
		{
			System.out.println(output);
			Object obj = parser.parse(output);
			JSONObject jsonObject = (JSONObject) obj;
			JSONArray responseMsg = (JSONArray) jsonObject.get("shipped_books");
			String []msgFormat = new String[responseMsg.size()];
			for(int i=0;i<responseMsg.size();i++)
			{
				JSONObject books = (JSONObject) responseMsg.get(i);
				msgFormat[i]= books.get("isbn").toString() +":"+"\""+books.get("title").toString()+"\""+":"+"\""+books.get("category").toString()+"\""+":"+"\""+books.get("coverimage").toString()+"\"";
				System.out.println(msgFormat[i]);

			}
			String user = env("APOLLO_USER", "admin");
			String password = env("APOLLO_PASSWORD", "password");
			String host = env("APOLLO_HOST", "54.193.56.218");
			int port = Integer.parseInt(env("APOLLO_PORT", "61613"));
			String destination = "/topic/96933.book";

			StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
			factory.setBrokerURI("tcp://" + host + ":" + port);

			Connection connection = factory.createConnection(user, password);
			connection.start();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			for(int i=0;i<msgFormat.length;i++)
			{
				JSONObject books = (JSONObject) responseMsg.get(i);
				Destination dest = new StompJmsDestination(destination+"."+books.get("category").toString());
				MessageProducer producer = session.createProducer(dest);
				producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

				String data = msgFormat[i];
				TextMessage msg = session.createTextMessage(data);
				msg.setLongProperty("id", System.currentTimeMillis());
				producer.send(msg);
				System.out.println(msg);
				if(i==msgFormat.length-1)
				{
					producer.send(session.createTextMessage("SHUTDOWN"));
				}
			}
		}
		conn.disconnect();
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void postToPublisher(String str)
	{
		try {
			URL url = new URL("http://54.193.56.218:9000/orders");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			String input = "{\"id\":\"96933\",\"order_book_isbns\":["+str+"]}";

			OutputStream os = conn.getOutputStream();
			os.write(input.getBytes());
			os.flush();

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));

			String output;
			System.out.println("Output from Server .... \n");
			while ((output = br.readLine()) != null) {
				System.out.println(output);
			}

			conn.disconnect();

		} catch (MalformedURLException e) {

			e.printStackTrace();

		} catch (IOException e) {

			e.printStackTrace();

		}
	}

	public String consumer() throws JMSException
	{
		String user = env("APOLLO_USER", "admin");
		String password = env("APOLLO_PASSWORD", "password");
		String host = env("APOLLO_HOST", "54.193.56.218");
		int port = Integer.parseInt(env("APOLLO_PORT", "61613"));
		String queue = "/queue/96933.book.orders";
		String destination = queue;
		String temp= "";

		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + host + ":" + port);

		Connection connection = factory.createConnection(user, password);
		connection.start();
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		Destination dest = new StompJmsDestination(destination);

		MessageConsumer consumer = session.createConsumer(dest);
		System.out.println("Waiting for messages from " + queue + "...");

		while(true)
		{
			Message msg = consumer.receive(5000);
			System.out.println("consuming");
			if(msg == null)
			{
				break;
			}

			if( msg instanceof TextMessage )
			{
				System.out.println("consuming");
				String body = ((TextMessage) msg).getText();
				System.out.println("Received message = " + body);

				temp = body.split(":")[1];

				temp = temp+",";
				if( "SHUTDOWN".equals(body))
				{
					break;
				}
			}
			else {
				System.out.println("Unexpected message type: "+msg.getClass());
			}
		}
		connection.close();
		if(temp != "")
		{
			System.out.println("******temp string******: "+temp);
			return temp.substring(0,temp.length()-1);
		}
		else
			return null;
	}
	
	private static String env(String key, String defaultValue) {
		String rc = System.getenv(key);
		if( rc== null ) {
			return defaultValue;
		}
		return rc;
	}

}
