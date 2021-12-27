import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Overlay {

  //GUI
  //----
  JFrame f = new JFrame("Cliente de Testes");
  JButton playButton = new JButton("Start");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Stop");
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  ImageIcon icon;


  //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received from the server (to receive)
  DatagramPacket senddp;
  DatagramSocket socketReceive; //socket to be used to send and receive UDP packet
  static int receivePort = 3333; //port where the client will receive the RTP packets
  DatagramSocket socketSend;
  static int sendPort = 4444;
  InetAddress NextNodeIPAddr; //Client IP address


  
  Timer cTimer; //timer used to receive data from the UDP socket
  byte[] cBuf; //buffer used to store data received from the server 
 
  //--------------------------
  //Constructor
  //--------------------------
  public Overlay() {

    //build GUI
    //--------------------------
 
    //Frame
    f.addWindowListener(new WindowAdapter() {
       public void windowClosing(WindowEvent e) {
	 System.exit(0);
       }
    });

    //Buttons
    buttonPanel.setLayout(new GridLayout(1,0));
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);

    // handlers... (so dois)
    playButton.addActionListener(new playButtonListener());
    tearButton.addActionListener(new tearButtonListener());

    //Image display label
    iconLabel.setIcon(null);
    
    //frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    iconLabel.setBounds(0,0,380,280);
    buttonPanel.setBounds(0,280,380,50);

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(390,370));
    f.setVisible(true);

    //init para a parte do cliente
    //--------------------------
    cTimer = new Timer(20, new overlayTimerListener());
    cTimer.setInitialDelay(0);
    cTimer.setCoalesce(true);
    cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

    try {
      // socket e video
      socketReceive = new DatagramSocket(receivePort); //init RTP socket (o mesmo para o cliente e servidor)
      socketReceive.setSoTimeout(5000); // setimeout to 5s
      socketSend = new DatagramSocket(sendPort); //init RTP socket (o mesmo para o cliente e servidor)
      socketSend.setSoTimeout(5000); // setimeout to 5s
      NextNodeIPAddr = InetAddress.getByName("10.0.0.20");
    } catch (SocketException e) {
      System.out.println("Cliente: erro no socket: " + e.getMessage());
    } catch (Exception e) {
        System.out.println("Servidor: erro no video: " + e.getMessage());
    }
  }

  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception
  {
        Overlay t = new Overlay();
  }


  //------------------------------------
  //Handler for buttons
  //------------------------------------

  //Handler for Play button
  //-----------------------
  class playButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

    System.out.println("Play Button pressed !"); 
	      //start the timers ... 
	      cTimer.start();
	    }
  }

  //Handler for tear button
  //-----------------------
  class tearButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      System.out.println("Teardown Button pressed !");  
	  //stop the timer
	  cTimer.stop();
	  //exit
	  System.exit(0);
	}
    }

  //------------------------------------
  //Handler for timer (para cliente)
  //------------------------------------
  
  class overlayTimerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      
      //Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(cBuf, cBuf.length);


      try{
        //receive the DP from the socket:
        socketReceive.receive(rcvdp);
          
        //create an RTPpacket object from the DP
        RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

        //print important header fields of the RTP packet received: 
        System.out.println("Got RTP packet with SeqNum # "+rtp_packet.getsequencenumber()+" TimeStamp "+rtp_packet.gettimestamp()+" ms, of type "+rtp_packet.getpayloadtype());
        
        //print header bitstream:
        rtp_packet.printheader();

        //get to total length of the full rtp packet to send
        int packet_length = rtp_packet.getlength();

        //retrieve the packet bitstream and store it in an array of bytes
        byte[] packet_bits = new byte[packet_length];
        rtp_packet.getpacket(packet_bits);

        senddp = new DatagramPacket(packet_bits, packet_length, NextNodeIPAddr, sendPort);
	    socketSend.send(senddp);

      }
      catch (InterruptedIOException iioe){
	      System.out.println("Nothing to read");
      }
      catch (IOException ioe) {
	      System.out.println("Exception caught: "+ioe);
      }
    }
  }

}//end of Class Overlay


































/*import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Overlay {

    DatagramSocket socketReceive;
    DatagramSocket socketSend;
    DatagramPacket rcvdp;
    DatagramPacket senddp;
    int portReceive = 3333;  
    int portSend = 4444;  
    InetAddress ClientIPAddr; //Client IP address

    byte[] cBuf; //buffer used to store data received from the server 
    

    public Overlay(){
        
        cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

         try {
            socketReceive = new DatagramSocket(); 
            socketSend = new DatagramSocket();
            ClientIPAddr = InetAddress.getByName("10.0.0.20");
            System.out.println("Servidor: socket " + ClientIPAddr);

        } catch (SocketException e) {
            System.out.println("Servidor: erro no socket: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Servidor: erro no video: " + e.getMessage());
        }

        //Construct a DatagramPacket to receive data from the UDP socket
        rcvdp = new DatagramPacket(cBuf, cBuf.length);
        
            try{
            //receive the DP from the socket:
            socketReceive.receive(rcvdp);
            
            //create an RTPpacket object from the DP
            RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

            //get to total length of the full rtp packet to send
            int packet_length = rtp_packet.getlength();

            //retrieve the packet bitstream and store it in an array of bytes
            byte[] packet_bits = new byte[packet_length];
            rtp_packet.getpacket(packet_bits);

            //send the packet as a DatagramPacket over the UDP socket 
            senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, portSend);
            socketSend.send(senddp);

            rtp_packet.printheader();

            }
            catch (InterruptedIOException iioe){
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }
        
    }

     public static void main(String argv[]) throws Exception {
        Overlay o = new Overlay();
  }
}

*/










































/*public class Overlay{
    //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received from the server (to receive)
  DatagramPacket snddp; //UDP packet received from the server (to receive)
  DatagramSocket socketReceive; //socket to be used to receive UDP packet
  DatagramSocket socketSend; //socket to be used to send UDP packet
  static int RTP_port = 4444; //port where the client will receive the RTP packets

  public Overlay(){
    try {
        socketReceive = new DatagramSocket(RTP_port); //init socketReceive
        socketReceive.setSoTimeout(5000); // setimeout to 5s

        socketSend = new DatagramSocket(RTP_port); //init socketSend
        socketSend.setSoTimeout(5000); // setimeout to 5s
    } catch (SocketException e) {
        System.out.println("Client: error in socket: " + e.getMessage());
    }   
  }

  public void HandleNode(){
      //receive the DP from the socket:
      socketReceive.receive(rcvdp);   

    //get to total length of the full rtp packet to send
	  int packet_length = snddp.getlength();

    //retrieve the packet bitstream and store it in an array of bytes
	  byte[] packet_bits = new byte[packet_length];
	  rcvdp.getpacket(packet_bits);

      NextNodeAddr = InetAddress.getByName("10.0.0.20");

    //send the packet as a DatagramPacket over the UDP socket 
	  snddp = new DatagramPacket(packet_bits, packet_length, NextNodeAddr, RTP_port);
	  socketSend.send(snddp);
  }

  public static void main(String argv[]) throws Exception{
      Overlay o = new Overlay();

      HandleNode();
  }

  

}*/