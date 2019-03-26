import java.net.*	;
import java.io.*	; 
import java.util.*	;
/* Comments would be added later 
 * 
 * - Ratheep
 * */
 
class Logger
{
	static PrintWriter out;
	String loggerName;
	boolean enabled;
	private	static HashMap<String,Logger> loggers=new HashMap<String,Logger>();	
	public 	static Logger logger(String loggername)
	{
		return loggers.get(loggername);
	}	
	public static void file(String filename)
	{
		try
		{
			out=new PrintWriter(new FileWriter(filename),true);
		}
		catch(IOException ie)
		{
			System.err.println("File Error");
		}
	}	
	public static PrintWriter sys()
	{
		PrintWriter temp=out;
		out=new PrintWriter(System.out,true);		
		return temp;
	}
	public Logger(String name)
	{
		loggerName=name;
		loggers.put(name,this);
		enable();		
	}
	public void enable()
	{
		enabled=true;
	}
	public void disable()
	{
		enabled=false;
	}
	public void println(String toPrint)
	{	
		if(enabled)
		{								
			out.println(Thread.currentThread()+ " : " + loggerName  + " : "+ toPrint);		
			out.flush();
		}
	}
	public void write(byte[] bytes)
	{	
		if(enabled)
		{								
			out.println(Thread.currentThread()+" : "+loggerName + " : Bytes : \n" );
			for(byte b:bytes)
			out.print(b);
		}
	}
	public static void close()
	{		
		out.close();		
	}
	public void finalize()
	{
		close();
	}
}
//////////////////////////////////////////////////////////////////////////////////////////////////////
 
class Headers
{
	ArrayList <String>			keys			;
	HashMap<String,String>		headers			;
	Logger log=new Logger("Headers");
	
	public Headers()
	{
		keys		= new ArrayList<String>();
		headers		= new HashMap<String,String>();
	}	
	
	public void parse(BufferedReader br)
	{		
		String headerLine;
		try
		{
			while((headerLine=br.readLine())!=null )
			{
				if( headerLine.equals("")) break;
				String [] hdrWords       = headerLine.split(":",2);				
				
				headers.put(hdrWords[0],hdrWords[1].trim());
				keys.add(hdrWords[0]);
				log.println("Parse: "+hdrWords[0]+ ": " +hdrWords[1]);
			}
		}
		catch(IOException e)
		{
			log.println("IO at parse error");
		}
	}
	public void copy(Headers hdrs)
	{
		headers.clear();
		keys.clear();
		for(String el: hdrs.keys)
		{	
			//log.println(el + " " + hdrs.value(el) );
			keys.add(el);
			headers.put(el,hdrs.value(el));
		}		
	}
	public void print(PrintWriter pw)
	{
		for(String el:keys)
		{
			pw.print( el +": " + value(el)+ "\r\n" );
			log.println("print: "+el + ": " + value(el) );			
		}
		pw.flush();
		
	}

	public void clear()
	{
		headers.clear();
		keys.clear();
	}
	public String value(String key)
	{
		String ret="";
		String val=headers.get(key);
		
		if(val!=null)
			ret=val;
			
		return ret;		
	}
	public void modify(String key,String value)
	{
		keys.add(key);
		headers.put(key,value);
	}
	
	
}
class HTTPMessage
{
		
	OutputStream 				netOut			;
	InputStream 				netIn			;
	ByteArrayInputStream		bin				;
	ByteArrayOutputStream		bout			;
	BufferedReader				reader			;
	PrintWriter					writer			;
	Headers 					headers;
	boolean						proxyAlive		;
	boolean						keepAlive		;
	int							messageLength	;
	Socket						remoteSocket	;
	ByteArrayOutputStream		bhex			;
	String 						prefix			;
	public boolean 	transferEncoding	= false	;
	public boolean	tunneling			= false ;
	Logger log=new Logger("HTTPMessage");
	public HTTPMessage(Socket socket)
	{
		remoteSocket= socket;
		try 
		{
			netIn		= socket.getInputStream();
			netOut		= socket.getOutputStream();
		}
		catch(IOException e)
		{
			log.println("Socket Problem");
		}
		headers		= new Headers();
	}
	public void readHeaderSection()
	{		
	
		
		int n;
	    int n1=0;
        int n2=0;
        int n3=0;
        byte[] bytes;
    	bout=new ByteArrayOutputStream();    
        
        try
        {
	        while( (n=netIn.read()) !=-1)
	        {
	           	bout.write(n);   
	           	
	        	if( n3==13 && n2==10 && n1==13 && n==10 )
	        	{
	        		break;
	    	    }	            	                	
	        	n3=n2;
	            n2=n1;
	    		n1=n;            		
	        }  			
	        bout.flush();
        }
        catch(IOException e)
        {
        		
        }
		bytes=bout.toByteArray();		
		bin = new ByteArrayInputStream(bytes);		
		try
		{
			reader=new BufferedReader(new InputStreamReader(bin,"UTF-8"));
		}
		catch(Exception e)
		{
			log.println("Error Buffered reader creation");
			
		}
	}
	
	
	public void writeHeaders()
	{		
		headers.print(writer);
		log.println("Written headers");
	}	
	
	public void writeHeaderSection()
	{	
		byte [] bytes;
		writer.print("\r\n");
		writer.flush();
		bytes=bout.toByteArray();	
		
		try
		{
			netOut.write(bytes);
			netOut.flush();
		}
		catch(IOException ie)
		{
			log.println("Request:writeHeaderSection");
		}
		
	}	
	public void writeMessage(InputStream in,int length)
	{
		int ch;
		int count =0;
		
		try
		{			
			if(transferEncoding)
			{
				log.println("Entering Chunking");
				int 	prevCh=0;
				String	lenStr="";								
				try
				{
						while((ch=in.read())!=-1)
						{
							log.println(""+ch);
							if(prevCh==13 && ch==10)
							{
								log.println("broke");
								break;		
							}
							if(ch!=10 && ch!=13)						
								lenStr	= lenStr+(char)ch;
							prevCh	= ch;
						}
						
						length = Integer.parseInt(lenStr,16);
						String prefix=lenStr+"\r\n" ;
						netOut.write(prefix.getBytes());
					
						while(length!=0)
						{
							log.println("Chunking " + length);
							count=0;
							while( count != length &&(ch=in.read())!=-1 )
							{	
								count ++;
								netOut.write(ch );							
							}				
							
							netOut.flush();	
							lenStr="";
							while((ch=in.read())!=-1)
							{
								netOut.write(ch);
								if(prevCh==13 && ch==10)
								{
									log.println("broke");
									break;		
								}								
								prevCh	= ch;
							}			
							while((ch=in.read())!=-1)
							{
								if(prevCh==13 && ch==10)
								{
									log.println("broke");
									break;		
								}
								if(ch!=10 && ch!=13)						
									lenStr	= lenStr+(char)ch;
								prevCh	= ch;
							}						
							length = Integer.parseInt(lenStr,16);
							log.println("last len field " + length);
							prefix=lenStr+"\r\n" ;
							netOut.write(prefix.getBytes());						
						}
										
				}
				catch(IOException ie)
				{
					log.println("IO reading chunk len");
					ie.printStackTrace();
				}				
			}
			else
			{
					log.println("Blocks at write message");
			
					while( count != length &&(ch=in.read())!=-1 )
					{	
						count ++;
						netOut.write(ch );	
						netOut.flush();	
					}
			}
			System.out.println("count" + count);
			netOut.flush();		
			log.println("UnBlocks at write message");
			
		}
		catch(IOException ie)
		{
			
		}
	}
	
	
}

class HTTPRequest extends HTTPMessage
{
	String 			version		;
	String 			method		;
	String 			url			;
	
	
	public HTTPRequest(Socket socket)
	{		
		super(socket);		
		log=new Logger("HTTPRequest");
	}
	public void readRequestLine()
	{		
		try
		{
			log.println("Blocks");
			String requestLine 	= reader.readLine();
			log.println("Un Blocks");
			log.println(requestLine);
			String rlWords[]	= requestLine.split("\\s",3);

			method	= rlWords[0].trim();
			url	= rlWords[1].trim();			
			version	= rlWords[2].trim();
			
			log.println("method  : " + method);
			log.println("Version : " + version);
			log.println("url     : " + url);	
			
		}
		catch(IOException ie)
		{
			ie.printStackTrace();
		}
	}
	public void readHeaders()
	{
		headers.parse(reader);
	}
	public void reset()
	{
		method=url=version=null;
		headers.clear();
	}	
	public int calculateLength()
	{
		
		boolean get 			= method.equals("GET");
		boolean connect			= method.equals("CONNECT");
		boolean transfercoding	= headers.value("Transfer-Encoding").equals("chunked");
		String 	contLenStr		= headers.value("Content-Length");
		boolean contentLength   = !contLenStr.equals("");
		if(get)
		{
			messageLength=0;			
		}
		else if(connect)
		{
			tunneling=true;
			messageLength=0;
		}
		else if(transfercoding)
		{
				int 	ch;
				int 	prevCh=0;
				String	lenStr="";
				ByteArrayOutputStream	bhex=new ByteArrayOutputStream() ;
				transferEncoding=true;
				try
				{
					while((ch=netIn.read())!=-1)
					{
						//\r\n = 13 10
						if( ch==13 || ch==10) log.println("Number Error");
						if(prevCh==13 && ch==10) break;						
						lenStr	= lenStr+(char)ch;
						bhex.write(ch);
						prevCh	= ch;
						
					}
					bhex.flush();
				}
				catch(IOException ie)
				{
					log.println("IO reading chunk len");
				}
				try
				{
				messageLength=Integer.parseInt(lenStr.trim(),16);
				}catch(Exception e)
				{
					
				}
		}
		else if(contentLength)
		{
			messageLength=Integer.parseInt(contLenStr);
		}
		else 
		{
			messageLength = -1;
		}
					
		return messageLength;
	}
	
	//////////////////writing
	
	public void writeRequestLine()
	{
		bout					= new ByteArrayOutputStream();
		writer 					= new PrintWriter(bout);
		String requestLine	= method + " " + url + " " + version ;		
		writer.print(requestLine + "\r\n");
		writer.flush();
		log.println("Request: " + requestLine);
	}
	
}

class HTTPResponse extends HTTPMessage
{
	String 			code;
	String 			reason;
	String 			version;
	int 			messageLength;
	
	public HTTPResponse(Socket socket)
	{
		super(socket);
		log=new Logger("HTTPResponse");
	}
	
	public void readStatusLine()
	{		
		try
		{
			log.println("Blocks");
			String statusLine 	= reader.readLine();
			log.println("Un Blocks");
			log.println(statusLine);
			String rlWords[]	= statusLine.split("\\s",3);

			version		= rlWords[0].trim();
			code		= rlWords[1].trim();			
			reason		= rlWords[2].trim();
			
			log.println("version : " + version );
			log.println("reason : " + reason);
			log.println("code     : " + code);
			
			
		}
		catch(IOException ie)
		{
			ie.printStackTrace();
		}
	}
	public void readHeaders()
	{
		headers.parse(reader);
	}
	public int calculateLength()
	{
		String 	contLenStr	  	= headers.value("Content-Length");
		boolean contentLength 	= !contLenStr.equals("");
		boolean transfercoding	= headers.value("Transfer-Encoding").equals("chunked");
		
		boolean code100			= code.equals("100");		
		boolean code101			= code.equals("101");
		boolean	code204			= code.equals("204");
		boolean	code304			= code.equals("304");
		
		if(code100||code101||code204||code304)
		{
			messageLength=0;
		}
		else if(contentLength)
		{
			messageLength=Integer.parseInt(contLenStr);			
		}
		else if(transfercoding)
		{
				transferEncoding=true;
		}	
		else
		{
			messageLength = -1;
		}
					
		return messageLength;
	}
	public void reset()
	{
		code=reason=version=null;
		headers.clear();
	}
	
	public void writeStatusLine()
	{
		bout					= new ByteArrayOutputStream();
		writer 					= new PrintWriter(bout);
		String statusLine		= version + " " + code + " " + reason ;		
		writer.print(statusLine + "\r\n");
		writer.flush();
		log.println("Response Status Line Written: " + statusLine);
	}	
}


////////////////////////////////////////////////////////////////////////////////////////////////////////
class BidirectionalThread extends Thread
{
	Socket 		inSocket;
	Socket		outSocket;	
	Logger log=new Logger("BidirectionalThread");
	BidirectionalThread rc=null;
	public BidirectionalThread()
	{
		
	}
	public BidirectionalThread(Socket clientSock,Socket remoteSock)
	{
		inSocket	= remoteSock;
		outSocket	= clientSock ;	
		
		BidirectionalThread bt=new BidirectionalThread();
		bt.inSocket	= clientSock;
		bt.outSocket= remoteSock;
		rc=bt;
		bt.start();
		bt.setName("C->R");
		this.setName("R->C");
		this.start();
	}
	
	public void run()
	{
		int ch;
		try
		{
				log.println("Thread Started"+this);
				InputStream in=inSocket.getInputStream();
				OutputStream out=outSocket.getOutputStream();
			
				while((ch=in.read())!=-1)
				{
						//System.err.print(""+ this +  (char)ch);
						out.write(ch);
				}
				if(rc!=null)
					rc.terminate();
				log.println("Thread Ended"+this);
		}
		catch(IOException ie)
		{
			ie.printStackTrace();
		}
			
		
	}
	public void socketClose()
	{
		try
		{
			inSocket.close();
			outSocket.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	public void terminate()
	{		
		if(rc!=null)rc.socketClose();
		//rc.stop();	
	}
	
	
}
class ThreadServer extends Thread
{
	Socket 		clientSocket;
	Socket		remoteSocket;	
	Logger log=new Logger("ThreadServer");

	public void copyBytes(InputStream in,OutputStream out,int length)
	{
		if(length == 0 ) return ;
		if(length == -1)
		{
			int ch;
			try
			{
				while((ch=in.read())!=-1)
				{
					out.write(ch);
				}
			}
			catch(IOException e)
			{
				log.println("Copy");
			}
		}
		
	}
	public ThreadServer()
	{
		super();
		this.start();
	}
	public ThreadServer(Socket clientSocket)
	{
		super();		
		this.clientSocket=clientSocket;
		try
		{
			remoteSocket=new Socket("proxy.cognizant.com",6050);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}	
		this.start();

	}
	public void run()
	{
		
		log.println("Run Called");
		
		boolean 	keepAlive		= false	;
		boolean 	proxyKeepAlive	= false	;
		boolean 	alive			= true	;
		boolean 	remoteAlive		= true	;
		boolean 	remoteKeepAlive	= false	;
		
		HTTPRequest		clientRequest	=null;
		HTTPResponse	clientResponse	=null;
		
		HTTPRequest		remoteRequest	=null;
		HTTPResponse	remoteResponse	=null;		
		
		
		while( alive )
		{				
				if(!proxyKeepAlive)
				{
					clientRequest= new HTTPRequest(clientSocket);					
					clientResponse=new HTTPResponse(clientSocket);
				}
				else
				{
					clientRequest.reset();
				}
				
				if( !remoteKeepAlive )
				{
						remoteRequest= new HTTPRequest(remoteSocket);
						remoteResponse = new HTTPResponse(remoteSocket);
				}	
					
				clientRequest.readHeaderSection();
				clientRequest.readRequestLine();
				clientRequest.readHeaders();
				int len=clientRequest.calculateLength();
				
				remoteRequest.version=clientRequest.version;
				remoteRequest.url=clientRequest.url;
				remoteRequest.method=clientRequest.method;
				
				//log.println("Calc Length=" +len );
				remoteRequest.writeRequestLine();
				remoteRequest.headers.copy(clientRequest.headers);
				remoteRequest.writeHeaders();
				remoteRequest.writeHeaderSection();				
				remoteRequest.writeMessage(clientRequest.netIn,len);
				
				log.println("Request over");
				log.println("Getting Response");
				
				
				remoteResponse.readHeaderSection();
				remoteResponse.readStatusLine();				
				remoteResponse.readHeaders();
				log.println("Headers retrieved from remote response");
				log.println("Writing to client");
				
				clientResponse.code		= remoteResponse.code;
				clientResponse.version	= remoteResponse.version;
				clientResponse.reason	= remoteResponse.reason;
				clientResponse.writeStatusLine();
				clientResponse.headers.copy(remoteResponse.headers);
				//clientResponse.headers.modify("Proxy-Connection","Close");
				clientResponse.writeHeaders();
				clientResponse.writeHeaderSection();
				boolean dontClose=false;
				if(clientRequest.tunneling)
				{
					if(remoteResponse.code.equals("200"))
					{
						dontClose=true;
						log.println("Entering Bidirectional Thread");
						new BidirectionalThread(clientSocket,remoteSocket);
					}
				}
				else
				{
				
					int len2=remoteResponse.calculateLength();				
					log.println("Remote Response  Length=" + len2);				
					clientResponse.transferEncoding=remoteResponse.transferEncoding;				
					clientResponse.writeMessage(remoteResponse.netIn,len2);				
				}
				int ch;
				/*try
				{
					log.println("Blocks");
					while((ch=remoteRequest.netIn.read())!=-1)
					{
						System.out.print((char)ch);
						System.out.flush();
					}
					log.println("UnBlocks");
				}
				catch(IOException ie)
				{
					ie.printStackTrace();
				}
				*/
				log.println("Request Processed");
				proxyKeepAlive=false;
				alive=false;
				try
				{
					if(!dontClose)
					{	
							sleep(100);					
							clientSocket.close();
							remoteSocket.close();
					}
					else
					{
						log.println("Bidirection Request Processed");
					}
				}
				catch(InterruptedException ie)
				{
					
				}
				catch(IOException ie)
				{
					ie.printStackTrace();
				}
		}
	}
}


class proxy
{
	ServerSocket serverSocket;
	Logger log=new Logger("Proxy");
	public proxy()
	{
		try
		{
				serverSocket=new ServerSocket(9090,5);		
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void process()
	{		
		Socket clientSocket;
		try
		{
			while((clientSocket= serverSocket.accept())!=null)
			{
				log.println("New Connection accepted");
				ThreadServer ts=new ThreadServer(clientSocket);
			}
		}
		catch(Exception e)
		{
			log.println("Error");
			e.printStackTrace();
		}
	}		
	public static void main(String args[])
	{
		Logger log=new Logger("Main");
		Logger.sys();
		//Logger.file("test.log");
		
		log.println("Starting");
		proxy hs=new proxy();
		hs.process();				
		Logger.close();
		
	}
}

