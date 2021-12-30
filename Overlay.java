import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import java.nio.charset.StandardCharsets;

public class Overlay {

  //GUI
  //----
  JFrame f = new JFrame("Overlay Node");
  JButton playButton = new JButton("Start");
  JButton tearButton = new JButton("Stop");
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  ImageIcon icon;


  //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received
  DatagramPacket senddp; //UDP packet to be sent
  DatagramSocket socketReceive; //socket to be used to send and receive UDP packet
  static int receivePort = 3333; //port where the client will receive the RTP packets
  DatagramSocket socketSend;
  //static int receivePort = 4444;
  InetAddress NextNodeIPAddr; //IP address of the node receiving the Packet
  ArrayList<String> vizinhos = new ArrayList<String>();

  Timer cTimer; //timer used to receive data from the UDP socket
  byte[] cBuf; //buffer used to store data received from the server 

  int bestHops = 999999;
  InetAddress bestHopsAddress = null;
  ArrayList<InetAddress> destinos = new ArrayList<>();
  int lastDiscoveryId=-1;

  //--------------------------
  //Constructor
  //--------------------------
  public Overlay(String argv[]) {

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

    // handlers
    playButton.addActionListener(new playButtonListener());
    tearButton.addActionListener(new tearButtonListener());

    //Image display label
    iconLabel.setIcon(null);
    
    //frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    iconLabel.setBounds(0,0,380,280);
    buttonPanel.setBounds(0,40,380,50);

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(385,140));
    f.setVisible(true);

    //init para a parte do cliente
    //--------------------------
    cTimer = new Timer(20, new overlayTimerListener());
    cTimer.setInitialDelay(0);
    cTimer.setCoalesce(true);
    cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

    for(int i = 0; i < argv.length; i++) {
        int v = i+1;  
        System.out.println("Vizinho " + v + ": "  + argv[i]);
        vizinhos.add(argv[i]);
    }

    try {
      // socket e video
      socketReceive = new DatagramSocket(receivePort); //init RTP socket (o mesmo para o cliente e servidor)
      socketReceive.setSoTimeout(5000); // setimeout to 5s
      socketSend = socketReceive ; //new DatagramSocket(receivePort); //init RTP socket (o mesmo para o cliente e servidor)
      //socketSend.setSoTimeout(5000); // setimeout to 5s
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
        
        Overlay t = new Overlay(argv);
  }


  //---------------------------------      String ---
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

      System.out.println("Stop Button pressed !");  
	  //stop the timer
	  cTimer.stop();
	  //exit
	  System.exit(0);
	}
    }

  //------------------------------------
  //Handler for timer (para cliente)
  //------------------------------------
  
  // Criação e envio de pacote para obtenção das melhores rotas. 
  private void sendControlMessage(String s, InetAddress address) throws IOException{
    byte[] msg = s.getBytes();
    byte[] data = new byte[msg.length+1];
    data[0]=0; //Marker para pacote de controlo. Primeiro byte == 0
    System.arraycopy(s.getBytes(),0,data,1,msg.length);
    senddp = new DatagramPacket(data, data.length, address, receivePort);
    socketSend.send(senddp);
  }


  class overlayTimerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      
      //Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(cBuf, cBuf.length);


      try{
        //receive the DP from the socket:
        socketReceive.receive(rcvdp);
        byte[] ba = rcvdp.getData() ;
        //Verifica se a versão do pacote identifica RTP ou obtenção de rota
        if ((ba[0] & 0xC0)/*b11000000*/ == 0){ //Se version == 0 
          //Mensagem de controlo a enviar
          String s = new String(ba,1,rcvdp.getLength()-1); //"Discovery,id,custo";

          System.out.println("Received: " +s );

          String[] cmd = s.split(",");

          if ("Discovery".equals(cmd[0])){
            int id = Integer.parseInt(cmd[1]);
            if (id != lastDiscoveryId){
              destinos = new ArrayList<>();
              lastDiscoveryId = id;
              bestHops=999999;
              System.out.println("New Optimal Route Discovered - Clearing destinos " + lastDiscoveryId);
            }

            int hops = Integer.parseInt(cmd[2]) + 1;
            
            
            if (hops < bestHops){
              //Se mensagem trouxer métrica menor, enviar mensagem para melhor nodo anterior para me remover de destinos
              if (bestHopsAddress!=null){
                sendControlMessage("RemoveClient", bestHopsAddress);
                System.out.println(bestHopsAddress + " removed from route");
              }
              //Actualizar informação 
              bestHops = hops;
              bestHopsAddress = rcvdp.getAddress();
              System.out.println("Best route from " + bestHopsAddress + " with hop cost of " + hops);

              s = "Discovery," + cmd[1]+","+hops;
              byte[] msg = s.getBytes();
              byte[] data = new byte[msg.length+1];
              data[0]=0; //Marker para pacote de controlo. Primeiro byte == 0
              System.arraycopy(s.getBytes(),0,data,1,msg.length);

              //Enviar para todos os vizinhos excepto o que nos enviou
              for(String vizinho: vizinhos){
                if (!vizinho.equals(bestHopsAddress.toString())){
                  System.out.println("Send to neighbour " + vizinho);
                  //Enviar pacote 
                  senddp = new DatagramPacket(data, data.length, InetAddress.getByName(vizinho), receivePort);
                  socketSend.send(senddp);
                }
              }

              //if already has destinos must addClient to sender
              if (!destinos.isEmpty()){                
                sendControlMessage("AddClient", bestHopsAddress);
                System.out.println("New destino - " + rcvdp.getAddress());
              }

            }
          //Adicionar novo destino do próximo nodo Overlay caso tenha 
          //sido encontrada melhor rota pelo Cliente
          }else if ("AddClient".equals(cmd[0])){
            if (!destinos.contains(rcvdp.getAddress())){
              //Adicionar nodo à frente que enviou pedido como destino.           
              destinos.add(rcvdp.getAddress());
              //Enviar para melhor nodo anterior
              sendControlMessage("AddClient", bestHopsAddress);
              System.out.println("New destino - " + rcvdp.getAddress());
            }

          }else if ("RemoveClient".equals(cmd[0])){
            //Remover nodo que enviou dos destinos uma vez que foi encontrada melhor rota
            destinos.remove(rcvdp.getAddress());
            if (destinos.isEmpty()){
              sendControlMessage("removeClient", bestHopsAddress);
              //Enviar o mesmo pedido para o nodo que nos tinha como destino.
              System.out.println("Destinos is empty...");
            }
          }
        }else{

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

          for(InetAddress destino: destinos){
            senddp = new DatagramPacket(packet_bits, packet_length, destino/*NextNodeIPAddr*/, receivePort);
            socketSend.send(senddp);
          }
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

}//end of Class Overlay 


































/*import java.io.*;
import java.net.*;
import java.util.*;

public class Overlay {

  DatagramPacket rcvdp; //UDP packet received
  DatagramPacket senddp; //UDP packet to be sent
  DatagramSocket socketReceive; //socket to be used to send and receive UDP packet
  static int receivePort = 3333; //port where the client will receive the RTP packets
  DatagramSocket socketSend;
  static int receivePort = 4444;
  InetAddress NextNodeIPAddr; //IP address of the node receiving the Packet

  byte[] cBuf;

  public Overlay() {

    cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

    try {
      // socket e video
      socketReceive = new DatagramSocket(receivePort); //init RTP socket (o mesmo para o cliente e servidor)
      socketReceive.setSoTimeout(5000); // setimeout to 5s
      socketSend = new DatagramSocket(receivePort); //init RTP socket (o mesmo para o cliente e servidor)
      socketSend.setSoTimeout(5000); // setimeout to 5s
      NextNodeIPAddr = InetAddress.getByName("10.0.0.20");
    } catch (SocketException e) {
      System.out.println("Cliente: erro no socket: " + e.getMessage());
    } catch (Exception e) {
        System.out.println("Servidor: erro no video: " + e.getMessage());
    }

    while(true){
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

            senddp = new DatagramPacket(packet_bits, packet_length, NextNodeIPAddr, receivePort);
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

  public static void main(String argv[]) throws Exception{
        Overlay t = new Overlay();  
  }
}*/











































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