import java.net.*;
import java.io.*;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;


public class fclient extends Frame implements Runnable,ActionListener
 {

   Button file;
   FileDialog open,save;
   Socket fsoc=null;
   DataInputStream fin;
   PrintWriter fout;
   File f,f1;
   String msg1,msg2,url;
   RandomAccessFile rf,rf1;
   Thread t1=null;
   String m="\n";
  public fclient()
    {
     super("file transfer");
     setSize(400,400);
     setVisible(true);
     try
       {
 
        fsoc=new Socket("localhost",3032);
  
        fin=new DataInputStream(fsoc.getInputStream());
  
        fout=new PrintWriter(fsoc.getOutputStream(),true);
       }
       catch(Exception e)
      {}

 
  file=new Button("file");
 
   open=new FileDialog(this,"open file",0);
   save=new FileDialog(this,"save file",1);

   file.setBounds(150,340,100,30);


   add(file);


  file.addActionListener(this);
  addWindowListener(new win());
  }
    

   public class win extends WindowAdapter
     {
   public void windowClosing(WindowEvent we)
       {
         setVisible(false);
         dispose();
         System.exit(0);

       }
     }


     public void run()
       {
         try
           {
             do
               {
                  if(Thread.currentThread()==t1)
                    { 
   
                     msg1=fin.readLine();
                     
                     while(true)
                        {
                         if(msg1.startsWith("FILE"))
                           { 
                               save.show();
                               url=save.getDirectory()+save.getFile();
                               f1=new File(url);
                               rf1=new RandomAccessFile(f1,"rw");
                                int x;
                          while(!(msg1=fin.readLine()).endsWith("EOF"))

                                  {  
                                 
                                   rf1.writeBytes(msg1+"\n");
                                   
                                    
                                  }
                              rf1.close();

                           }
                        }
                    }
               }while(true);
          }
        catch(Exception e)
        {}
     }


    public void actionPerformed(ActionEvent ae)
     {
      if(ae.getSource()==file)
        {
         try
           {
             open.show();
             url=open.getDirectory()+open.getFile();
             fout.println("FILE");
             f=new File(url);
             rf=new RandomAccessFile(f,"r");
             while(rf.getFilePointer()<rf.length())
              {
                 msg2=rf.readLine();
                 fout.println(msg2);
                 
                 fout.flush();
              }

       fout.println("EOF");
       fout.flush();
       rf.close();

           }
       catch(Exception e)
           {}

       }
     }
public static void main(String args[])
    {
   fclient ft=new fclient();
   ft.t1=new Thread(ft);
   ft.t1.start();
  
    }

  }









