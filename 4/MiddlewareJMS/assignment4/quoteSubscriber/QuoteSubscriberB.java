package quoteSubscriber;

import java.util.*;
import java.io.*;

import javax.jms.*;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import stocks.*;

public class QuoteSubscriberB {

	// Vector to hold QuoteSubscriber threads-like objects
	static Vector<QuoteSubscriber> watchList = new Vector<QuoteSubscriber>();
	StockName tempStockName;
	StockID tempStockID;
	static StockIdentifier stockIdentifier;
	Company tempCompany;
	
	// JMS tools
	static TopicSession stockSubscribeSession;
	static QueueSession stockInitSession;
	static QuoteSubscriber quoteSubscriber;
	static TopicConnection topicConn;
	static QueueConnection queueConn;
	private static String url = ActiveMQConnection.DEFAULT_BROKER_URL;
	
	// Printing hook
	static boolean print = true;
	
	static int initTimeOut = 1000;
	
	
	public QuoteSubscriberB() {
		// constructor
	}
	
	
	/**
	 * <p>1. Create a new ActiveMQConnectionFactory from URL, which is set to ActiveMQConnection.DEFAULT_BROKER_URL
	 * <p>2. Use the ConnectionFactory to create a new TopicConnection and a new QueueConnection
	 * <p>3. Use Connections to create TopicSession for subscription and QueueSession for initialization
	 */
	private static void initializeJMS() throws Exception {
		/*
		 * JMS Pub/Sub Initialization, create session used by all topic publisher.
		 */
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
		topicConn = connectionFactory.createTopicConnection();
		queueConn = connectionFactory.createQueueConnection();
		topicConn.start();
		queueConn.start();
		stockSubscribeSession = topicConn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
		stockInitSession = queueConn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
	}
	
	/**
	 * Load subscribe list from SubscribeList.in file
	 * Reading from SubscribeList.in file line by line until null found
	 * Determine whether it is a stock name or ID, using it to create new
	 * QuoteSubscriber instance.
	 */
	private void loadFromIn(String fileName) throws JMSException, FileNotFoundException, IOException {
		System.out.print(" [Subscriber] Loading "+fileName+"...");
		String temp;
		BufferedReader in =
		new BufferedReader(new FileReader(fileName));
		
		while((temp = in.readLine()) != null) {
			if(temp.startsWith("DE")) {
				stockIdentifier = new StockID(temp);
			} else {
				stockIdentifier = new StockName(temp);
			}
			quoteSubscriber =
					new QuoteSubscriber(stockIdentifier, stockSubscribeSession, stockInitSession);
			quoteSubscriber.print = true;
			// Insert and start a new QuoteSubscriber thread
			watchList.addElement(quoteSubscriber);
			// quoteSubscriber.setup();
		}
		in.close();
		System.out.print("...Successfully!\n");
		// Fast initialization
		this.start();
	}
	
	/**
	 * Load watch list from serialized object file
	 */
	@SuppressWarnings("unchecked")
	private void loadFromSer(String fileName) throws
	IOException, ClassNotFoundException, JMSException {
		System.out.println(" [Subscriber] Loading "+fileName+"......");
		FileInputStream fileIn =
				new FileInputStream(fileName);
		ObjectInputStream in =
				new ObjectInputStream(fileIn);
		watchList = (Vector<QuoteSubscriber>) in.readObject();
		for (QuoteSubscriber q : watchList) {
			// q.dateFormat = new SimpleDateFormat("yyyyMMddHHmmssz");
			q.topicSession = stockSubscribeSession;
			q.queueSession = stockInitSession;
			q.print = true;
			// System.out.println(q.topicSubscriber);
			
			// System.out.println(q.topicSubscriber);
		}
		
		System.out.println(" [Subscriber] ......load "+fileName+" successfully!");
		in.close();
		fileIn.close();
		this.start();
	}
	
	private void start() throws JMSException {
		System.out.println(" [Subscriber] Fast initialization...");
		for (QuoteSubscriber q : watchList) {
			q.fastInit();
		}
		
		// wait for initialization finished
		try {
			Thread.sleep(initTimeOut);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		/*
		 * Another way to test initialization
		 * Need to improve
		 */
		for (QuoteSubscriber q : watchList) {
			// System.out.println(q.init);
			if (!q.init) {
				System.out.println(" [Subscriber] initialize "+q.subject+" timeout!");
			}
		}
		System.out.println(" [Subscriber] ...initialization finished!");
		
		for (QuoteSubscriber q : watchList) {
			q.setup();
		}
		
		print = false;
	}
	
	/**
	 * Use stockIdentifier to subscribe a new stock
	 */
	private static void subscribeNewStock(String s) throws JMSException, StockException {
		// Use s to create stock identifier
		StockIdentifier si;
		if (s.startsWith("DE")) {
			si = new StockID(s);
		} else {
			si  = new StockName(s);
		}
		// Determine whether stock already exists in watch list
		for (QuoteSubscriber q : watchList) {
			if (s.equals(q.s.getValue())) {
				throw new StockException("Stock exists!");
			}
		}
		// Create new StockSubscriber
		quoteSubscriber =
				new QuoteSubscriber(si, stockSubscribeSession, stockInitSession);
		// Insert and start a new QuoteSubscriber thread
		watchList.addElement(quoteSubscriber);
		quoteSubscriber.fastInit();
		try {
			Thread.sleep(initTimeOut);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!quoteSubscriber.init) {
			System.out.println(" [Subscriber] Subscribe "+s+" failed!");
		} else {
			System.out.println(" [Subscriber] Subscribe "+s+" successfully!");
		}
		quoteSubscriber.setup();
	}
	
	/**
	 * cancel subscription of an existing stock
	 */
	public static void unsubscribeStock(String s) throws StockException, JMSException {
		for (QuoteSubscriber q : watchList) {
			if (s.equals(q.s.getValue())) {
				q.topicSubscriber.close();
				watchList.removeElement(q);
				return;
			}
		}
		throw new StockException("Stock doesn't exist!");
	}

	public static void main(String[] args) {
		QuoteSubscriberB qb = new QuoteSubscriberB();
		// Using myJMS initialization to initialize JMS
		//System.out.println("I'm QuoteSubscriberB constructor!!");
		try {
			initializeJMS();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		String fileName;
		String command = "";
		String userStockIdentifier;
		
		
		Console console = System.console();
		if (console == null) {
			System.err.println(" [Subscriber] No console found!");
			System.exit(1);
		}
		
		System.out.println("***************************************************\n");
		
		
		/*
		 * First step, reading console command to load from .in/.ser file
		 */
		while (true) {
			fileName = console.readLine("Enter file name to load watch list (e.g. sub.in): ");
			if (fileName.endsWith(".in")) {
				try {
					qb.loadFromIn(fileName);
					break;
				} catch (FileNotFoundException e) {
					System.out.print("...not found!");
				} catch (IOException e) {
					System.out.println("...failed! Please try again!");
					//e.printStackTrace();
				} catch (JMSException e) {
					System.out.println("JMS setup failed!");
					e.printStackTrace();
				}
			} else if (fileName.endsWith(".ser")) {
				try {
					qb.loadFromSer(fileName);
					break;
				} catch (IOException e) {
					System.out.println("Load file failed! Please try again!");
					//e.printStackTrace();
				} catch (ClassNotFoundException e) {
					System.out.println("Class error, please check the file and try again!");
					//e.printStackTrace();
				} catch (JMSException e) {
					System.out.println("JMS setup failed!");
					e.printStackTrace();
				}
			} else {
				try {
					qb.loadFromSer("watchList.ser");
					break;
				} catch (IOException e) {
					System.out.println("Please enter a valid file name, " +
							"it should end with .ser or .in");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		System.out.println("***************************************************\n");
		
		/*
		 * Reading command to add/delete stock
		 * or exit to quit program
		 */
		System.out.println("Enter 'add' to subscribe a new stock, " +
				"'delete' to unsubscribe an exist stock, 'p' to enable/disable " +
				"stock quote printing, 'exit' to quite.");
		while (true) {
			command = console.readLine("Command>> ");
			if (command.equals("add")) {
				userStockIdentifier = console.readLine("Enter name or ID>> ");
				try {
					subscribeNewStock(userStockIdentifier);
				} catch (JMSException e) {
					System.out.println(" [Subscriber] Failed to subscribe a new stock");
					e.printStackTrace();
				} catch (StockException e) {
					System.out.println(" [Subscriber] Exception: "+e.getError());
				}
			} else if (command.equals("delete")) {
				userStockIdentifier = console.readLine("Enter name or ID>> ");
				try {
					unsubscribeStock(userStockIdentifier);
				} catch (StockException e) {
					System.out.println(" [Subscriber] Exception: "+e.getError());
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (command.equals("p")) {
				print = !print;
				for (QuoteSubscriber q : watchList) {
					q.print = print;
				}
				System.out.println(" [Subscriber] "+(print?"Start":"Stop")+" printing!");
			} else if (command.equals("exit")) {
				break;
			} else {
				System.out.println("Invalid command!");
			}
		}
		
		
		System.out.println("***************************************************\n");
		// Close topic session and connection
		try {
			stockSubscribeSession.close();
			stockInitSession.close();
			topicConn.close();
			queueConn.close();
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println(" [Subscriber] Connection closed!\n");
		command = console.readLine("Enter the file name that you want to " +
				"serialize object into\nDefault: watchList.ser\n" +
				"NOTE: it should end with .ser\n>> ");
		// System.out.println(command.toString());
		
		// Serialize object into file
		while (true) {
			try {
				FileOutputStream fileOut =
						new FileOutputStream(command);
				ObjectOutputStream out =
						new ObjectOutputStream(fileOut);
				System.out.println(" [Subscriber] Serializing object into "+command+"...");
				for (QuoteSubscriber q : watchList) {
					System.out.print(" [Subscriber] "+q.getStockIdentifier().getValue()+"\n\t\t");
					System.out.printf("%.2f", q.currentQuote);
					System.out.println("\t\t| "+q.currentTime);
				}
				out.writeObject(watchList);
				out.close();
				fileOut.close();
				break;
			} catch (FileNotFoundException e) {
				command = "watchList.ser";
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println(" [Subscriber] ...Object serialized successfully!");
		System.out.println("***************************************************\n");
		System.out.println("\nBye!");
		// Exit program
		System.exit(0);
		
	}

}
