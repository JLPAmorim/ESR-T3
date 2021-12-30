import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;


public class Servidor {

  //GUI:
  //----------------
  JFrame f = new JFrame("Servidor");
  JButton rotasButton = new JButton("Obter Rotas");
  JButton streamButton = new JButton("Stream");
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  ImageIcon icon;
  JLabel label;

  //RTP variables:
  //----------------
  DatagramPacket rotasdp; //UDP packet containing the message to obtain every route
  DatagramPacket senddp; //UDP packet containing the video frames (to send)A
  DatagramSocket RTPsocket; //socket to be used to send and receive UDP packet
  int RTP_dest_port = 3333; //destination port for RTP packets 
  InetAddress O2IPAddr; //First node 02 IP address
  
  static String VideoFileName; //video file to request to the server

  //Video constants:
  //------------------
  int imagenb = 0; //image nb of the image currently transmitted
  VideoStream video; //VideoStream object used to access video frames
  static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
  static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
  static int VIDEO_LENGTH = 500; //length of the video in frames

  Timer sTimer; //timer used to send the images at the video frame rate
  byte[] rotas;
  byte[] sBuf; //buffer used to store the images to send to the client 

  int discoveryId = 0;

  //--------------------------
  //Constructor
  //--------------------------
  public Servidor() {
    //init Frame
    

    //Buttons
    buttonPanel.setLayout(new GridLayout(1,0));
    buttonPanel.add(rotasButton);
    buttonPanel.add(streamButton);
    

    // handlers
    rotasButton.addActionListener(new rotasButtonListener());
    streamButton.addActionListener(new streamButtonListener());

    //Image display label
    iconLabel.setIcon(null);
    
    //frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    iconLabel.setBounds(0,0,380,280);
    buttonPanel.setBounds(0,40,380,50);

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(390,140));
    f.setVisible(true);

    // init para a parte do servidor
    sTimer = new Timer(FRAME_PERIOD, new serverTimerListener()); //init Timer para servidor

    /*sTimer = new Timer(FRAME_PERIOD, new ActionListener(){
      public void ActionPerformed(ActionEvent evt){
        dfjkhdlfkjh
      }
    }*/

    sTimer.setInitialDelay(0);
    sTimer.setCoalesce(true);
    sBuf = new byte[15000]; //allocate memory for the sending buffer

    try {
  	    RTPsocket = new DatagramSocket(); //init RTP socket 
        O2IPAddr = InetAddress.getByName("10.0.0.1");
        System.out.println("Servidor: socket " + O2IPAddr);
	      video = new VideoStream(VideoFileName); //init the VideoStream object:
        System.out.println("Servidor: vai enviar video da file " + VideoFileName);

    } catch (SocketException e) {
        System.out.println("Servidor: erro no socket: " + e.getMessage());
    } catch (Exception e) {
        System.out.println("Servidor: erro no video: " + e.getMessage());
    }

    //Handler to close the main window
    f.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
      //stop the timer and exit
      sTimer.stop();
      System.exit(0);
      }});

    //GUI:
    label = new JLabel("Send frame #        ", JLabel.CENTER);
    f.getContentPane().add(label, BorderLayout.CENTER);
          
    
  }

  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception
  {
    //get video filename to request:
    if (argv.length >= 1 ) {
        VideoFileName = argv[0];
        System.out.println("Servidor: VideoFileName indicado como parametro: " + VideoFileName);
    } else  {
        VideoFileName = "movie.Mjpeg";
        System.out.println("Servidor: parametro não foi indicado. VideoFileName = " + VideoFileName);
    }

    File f = new File(VideoFileName);
    if (f.exists()) {
        //Create a Main object 
        Servidor s = new Servidor();
    } else
        System.out.println("Ficheiro de video não existe: " + VideoFileName);
  }

  //Handler for Rotas button
  //-----------------------
  class rotasButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      try {
          String s = "Discovery,"+discoveryId+",0";
          discoveryId++;
          byte[] msg = s.getBytes();
          byte[] data = new byte[msg.length+1];
          data[0]=0; //marker for control packet
          System.arraycopy(s.getBytes(),0,data,1,msg.length);

          //send the packet 
          senddp = new DatagramPacket(data, data.length, O2IPAddr, RTP_dest_port);
          RTPsocket.send(senddp);

      }
      catch(Exception ex){
        System.out.println("Exception caught: "+ex);
        System.exit(0);
      }
    } 
  }

  //Handler for Stream button
  //-----------------------
  class streamButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      System.out.println("Stream Button pressed !"); 
	    //start the timers ... 
	    sTimer.start();
	  }
  }

  //------------------------
  //Handler for timer
  //------------------------
  class serverTimerListener implements ActionListener{
    public void actionPerformed(ActionEvent e) {

      //if the current image nb is less than the length of the video
      if (imagenb < VIDEO_LENGTH){ 
        //update current imagenb
        imagenb++;
            
        try {
          //get next frame to send from the video, as well as its size
          int image_length = video.getnextframe(sBuf);

          //Builds an RTPpacket object containing the frame
          RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, sBuf, image_length);
          
          //get to total length of the full rtp packet to send
          int packet_length = rtp_packet.getlength();

          //retrieve the packet bitstream and store it in an array of bytes
          byte[] packet_bits = new byte[packet_length];
          rtp_packet.getpacket(packet_bits);

          //send the packet as a DatagramPacket over the UDP socket 
          rotasdp = new DatagramPacket(packet_bits, packet_length, O2IPAddr, RTP_dest_port);
          RTPsocket.send(rotasdp);

          System.out.println("Send frame #"+imagenb);
          //print the header bitstream
          rtp_packet.printheader();

          //update GUI
          //label.setText("Send frame #" + imagenb);
        }
        catch(Exception ex){
          System.out.println("Exception caught: "+ex);
          System.exit(0);
        }
      }else{
        //if we have reached the end of the video file, stop the timer
        sTimer.stop();
      }
    }
  }
  

}//end of Class Servidor
