import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Cliente {

  //GUI
  //----
  JFrame f = new JFrame("Cliente de Testes");
  JButton playButton = new JButton("Play");
  JButton tearButton = new JButton("Teardown");
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  ImageIcon icon;


  //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received from the server (to receive)
  DatagramPacket senddp; //UDP packet received from the server (to receive)
  DatagramSocket RTPsocket; //socket to be used to send and receive UDP packet
   static int RTP_RCV_PORT = 3333;//4444; //port where the client will receive the RTP packets
  
  Timer cTimer; //timer used to receive data from the UDP socket
  byte[] cBuf; //buffer used to store data received from the server 
 
  int bestHops = 999999;
  InetAddress bestHopsAddress = null;
  int lastDiscoveryId=-1;

  //--------------------------
  //Constructor
  //--------------------------
  public Cliente() {

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
    cTimer = new Timer(20, new clientTimerListener());
    cTimer.setInitialDelay(0);
    cTimer.setCoalesce(true);
    cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

    try {
      // socket e video
      RTPsocket = new DatagramSocket(RTP_RCV_PORT); //init RTP socket (o mesmo para o cliente e servidor)
      //sendSocket = new DatagramSocket(RTP_RCV_PORT);
      RTPsocket.setSoTimeout(5000); // setimeout to 5s
    } catch (SocketException e) {
      System.out.println("Cliente: erro no socket: " + e.getMessage());
    }
  }

  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception
  {
        Cliente t = new Cliente();
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

   private void sendControlMessage(String s, InetAddress address) throws IOException{
    byte[] msg = s.getBytes();
    byte[] data = new byte[msg.length+1];
    data[0]=0; //marker for control packet
    System.arraycopy(s.getBytes(),0,data,1,msg.length);
    senddp = new DatagramPacket(data, data.length, address, RTP_RCV_PORT);
    RTPsocket.send(senddp);
  }

  //------------------------------------
  //Handler for timer (para cliente)
  //------------------------------------
  
  class clientTimerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      
      //Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(cBuf, cBuf.length);

      try{
        //receive the DP from the socket:
        RTPsocket.receive(rcvdp);
          
        byte[] ba = rcvdp.getData() ;
        if ((ba[0] & 0xC0)/*b11000000*/ == 0){ //version == 0 
          //msg controlo
          String s = new String(ba,1,rcvdp.getLength()-1); //"Discovery,id,custo";

          System.out.println("Received: " +s );

          String[] cmd = s.split(",");

          if ("Discovery".equals(cmd[0])){
            int id = Integer.parseInt(cmd[1]);
            if (id != lastDiscoveryId){
              lastDiscoveryId = id;
              bestHops=999999;
              System.out.println("new discovery");
            }

            int hops = Integer.parseInt(cmd[2]) + 1;
            //se 
            if (hops < bestHops){
              if (bestHopsAddress!=null){
                sendControlMessage("RemoveClient", bestHopsAddress);
                System.out.println(bestHopsAddress + " removed from route");
              }

              bestHops = hops;
              bestHopsAddress = rcvdp.getAddress();
              System.out.println("best route from " + bestHopsAddress + " with cost " + hops);

              sendControlMessage("AddClient", bestHopsAddress);
              System.out.println("new client - " + rcvdp.getAddress());
            }
          }        
        }else{
          //create an RTPpacket object from the DP
          RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

          //print important header fields of the RTP packet received: 
          System.out.println("Got RTP packet with SeqNum # "+rtp_packet.getsequencenumber()+" TimeStamp "+rtp_packet.gettimestamp()+" ms, of type "+rtp_packet.getpayloadtype());
          
          //print header bitstream:
          rtp_packet.printheader();

          //get the payload bitstream from the RTPpacket object
          int payload_length = rtp_packet.getpayload_length();
          byte [] payload = new byte[payload_length];
          rtp_packet.getpayload(payload);

          //get an Image object from the payload bitstream
          Toolkit toolkit = Toolkit.getDefaultToolkit();
          Image image = toolkit.createImage(payload, 0, payload_length);
          
          //display the image as an ImageIcon object
          icon = new ImageIcon(image);
          iconLabel.setIcon(icon);
        }
      }
      catch (InterruptedIOException iioe){
	      System.out.println("Nothing to read");
      }
      catch (IOException ioe) {
	      System.out.println("Exception caught: "+ioe);
      }
    }
  }

}//end of Class Cliente

