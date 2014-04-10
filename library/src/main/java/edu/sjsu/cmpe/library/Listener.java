package edu.sjsu.cmpe.library;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsMessage;

import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.domain.Book.Status;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;

public class Listener {

	private String apolloUser;
	private String apolloPassword;
	private String apolloHost;
	private int apolloPort;
	private String stompTopic;
	private final LibraryServiceConfiguration configuration;
	private BookRepositoryInterface bookRepository;
	private long isbn[]= new long[3];
	private String title[] = new String[3];
	private String category[] = new String[3];
	private String coverImage[] = new String[3];
	private int i;
	private String port;

	public Listener(LibraryServiceConfiguration config, BookRepositoryInterface bookRepository) {
		this.configuration = config;
		this.bookRepository = bookRepository;
		apolloUser = configuration.getApolloUser();
		apolloPassword = configuration.getApolloPassword();
		apolloHost = configuration.getApolloHost();
		apolloPort = configuration.getApolloPort();
		stompTopic = configuration.getStompTopicName();
		port = ""+apolloPort;
	}

	public void listenerMsg()
	{
		while(true)
		{
			try{
				System.out.println("******Start******");
				String user = env("APOLLO_USER", apolloUser);
				String password = env("APOLLO_PASSWORD", apolloPassword);
				String host = env("APOLLO_HOST", apolloHost);
				int apolloport = Integer.parseInt(env("APOLLO_PORT", port));
				String destination = stompTopic;
				System.out.println("destination queue:: "+destination);
				StompJmsConnectionFactory stompJmsConnectionFactory = new StompJmsConnectionFactory();
				stompJmsConnectionFactory.setBrokerURI("tcp://" + host + ":" + apolloport);

				Connection connection = stompJmsConnectionFactory.createConnection(user, password);
				connection.start();
				Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
				Destination dest = new StompJmsDestination(destination);

				MessageConsumer messageConsumer = session.createConsumer(dest);
				System.currentTimeMillis();
				i=0;
				System.out.println("******Waiting for messages...");
				while(true) {
					Message msg = messageConsumer.receive();
					if( msg instanceof TextMessage ) {
						String messageBody = ((TextMessage) msg).getText();
						if( "SHUTDOWN".equals(messageBody) ) {
							System.out.println("SHUTING DOWN******");
							break;
						}
						System.out.println("Received message = " + messageBody);

						StringTokenizer strToken = new StringTokenizer(messageBody,":,\"");
						while(strToken.hasMoreTokens())
						{
							isbn[i]= Long.parseLong(strToken.nextToken());
							System.out.println("isbn is" + isbn[i]);

							title[i]=strToken.nextToken();
							System.out.println("Title is "+title[i]);

							category[i] = strToken.nextToken();
							System.out.println("Category is "+category[i]);

							coverImage[i] = strToken.nextToken()+":"+strToken.nextToken();
							System.out.println("Cover Img:: " +coverImage[i]);
							i++;
						}

						System.out.println("Text Message");
						if(i == 3)
							break;
					} else if (msg instanceof StompJmsMessage) {
						StompJmsMessage sJmsMsg = ((StompJmsMessage) msg);
						String body = sJmsMsg.getFrame().contentAsString();
						if ("SHUTDOWN".equals(body)) {
							System.out.println("SHUTING DOWN..StompJmsMessage");
							break;
						}
						System.out.println("Received message = " + body);
						System.out.println("Stomp Message");
					} else {
						System.out.println("Unexpected message type: "+msg.getClass());
					}

				}
				connection.close();
			}
			catch(JMSException e)
			{
			}
			System.out.println("Finding Book...");
			Book book=null;

			long ib;
			for(int i=0;i<3;i++)
			{
				ib= isbn[i];
				System.out.println("ib is"+ib);
				if(ib!=0)
				{
					book=bookRepository.getBookByISBN(ib);
					if(book!=null)
					{
						System.out.println("Book found");
						if(book.getStatus()==Status.lost);
						{
							System.out.println("isbn : " + book.getIsbn());
							System.out.println("title : "+book.getTitle());
							System.out.println("status : "+book.getStatus());

							bookRepository.updateLostToAvailable(ib,Status.available);
						}
					}
					else
					{
						Book newbook = new Book();
						newbook.setStatus(Status.available);
						newbook.setIsbn(isbn[i]);
						newbook.setTitle(title[i]);
						newbook.setCategory(category[i]);
						try {
							newbook.setCoverimage(new URL(coverImage[i]));
						} catch (MalformedURLException e) {
							e.printStackTrace();
						}

						bookRepository.insertNewBooks(ib, newbook);
					}
				}
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static String env(String key, String defaultValue)
	{
		String rc = System.getenv(key);
		if( rc== null )
		{
			return defaultValue;
		}
		return rc;
	}
}
