package com.gryzorz.thread.richthread.v1.example;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.gryzorz.thread.richthread.v1.ExecutionStoppedException;
import com.gryzorz.thread.richthread.v1.OperationNotAllowedException;
import com.gryzorz.thread.richthread.v1.RichThread;
import com.gryzorz.thread.richthread.v1.RichThreadListener;

/**
 * Example of how RichThread works and can be used.<br>
 * This class is self-executable, so do net hesitate to launch it to see stuff at work.<br>
 * <br>
 * The second part is just for GUI/self-launch purpose.<br>
 * Interresting things to notice here are :<br>
 *  - implementation of method execute()
 *  - Thread.yield if UI involved in the process
 *  - setPauseBreakpoint() and setStopBreakpoint()
 *  - handling of ExecutionStoppedException
 * 
 * @author Benoit Fernandez
 */
public class MyRichThread extends RichThread {
    @Override
    protected void execute() throws Exception {
        /*
         * In this example, our thread will endlessy increase a counter
         */
        try { /* I widely advise to have a one-block of try/catch capturing the
               * ExecutionStoppedException in order to handle once the interruption */
            int i = 0;
            while (!doTerminateNormally) {
                Thread.currentThread().sleep(100);
                if(i%20 == 0) {
                    /* we allow to pause only on multiples of 20 */
                    setPauseBreakpoint();
                }
                /* my work */
                i = i+1;
                setCurrentValue(i); /* triggers a UI refresh */
                /* simulates a runtime exception by a GUI button */
                if(doGenerateException) {
                    doGenerateException = false;
                    throw new RuntimeException("Nasty RuntimeException thrown !");
                }
                if(i%50 == 0) {
                    /* we allow to stop only on multiples of 50 */
                    setStopBreakpoint();
                }
            }
            doTerminateNormally = false;
        } catch (ExecutionStoppedException e) {
            /* do cleanup stuff */
            System.out.println("Stopping...");
            Thread.currentThread().sleep(2000); //simulate heavy duty cleanup
            System.out.println("Stopped !");
            /* /!\ IMPORTANT : note that handling this exception must effectively put
             * and end to the execute() method */
        }
    }


    /**************************************************************************
     *                    test methods and GUI part                           *
     *************************************************************************/

    static final MyRichThread t = new MyRichThread();
    /**
     * Control thread from outside 
     */
    static final ButtonRestart buttonRestart = new ButtonRestart(t);
    static final ButtonStart buttonStart = new ButtonStart(t);
    static final ButtonStop buttonStop = new ButtonStop(t);
    static final ButtonPause buttonPause = new ButtonPause(t);
    static final ButtonResume buttonResume = new ButtonResume(t);
    
    /**
     * simulate thread normal termination or exception thrown
     */
    static final ButtonNormalTermination buttonNormalTermination = new ButtonNormalTermination(t);
    static boolean doTerminateNormally = false;
    static final ButtonGenerateException buttonGenerateException = new ButtonGenerateException(t);
    static boolean doGenerateException = false;
    
    public static void main(String[] args) {
        JFrame jf = new JFrame("Test RichThread");
        jf.setSize(640, 150);
        jf.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        eastPanel.add(buttonNormalTermination, BorderLayout.CENTER);
        eastPanel.add(buttonGenerateException, BorderLayout.SOUTH);
        jf.add(eastPanel, BorderLayout.EAST);
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new FlowLayout());
        jf.add(southPanel, BorderLayout.SOUTH);
        southPanel.add(buttonRestart);
        southPanel.add(buttonStart);
        southPanel.add(buttonStop);
        southPanel.add(buttonPause);
        southPanel.add(buttonResume);
        jf.add(t.currentNum, BorderLayout.CENTER);
        jf.add(t.state, BorderLayout.NORTH);
        t.addListener(new RichThreadListener() {
            @Override
            public void threadCrashed(Throwable tt) {
                t.setStateLabelText("Crashed");
                refreshButtonAvailability();
            }
            @Override
            public void threadPaused() {
                t.setStateLabelText("Paused");
                refreshButtonAvailability();
            }
            @Override
            public void threadRunning() {
                t.setStateLabelText("Running");
                refreshButtonAvailability();
            }
            @Override
            public void threadStoppedAfterStopRequest() {
                t.setStateLabelText("Stopped after a demand");
                refreshButtonAvailability();
            }
            @Override
            public void threadStopping() {
                t.setStateLabelText("Stopping");
                refreshButtonAvailability();
            }
            @Override
            public void threadRunningWaitingToPause() {
                t.setStateLabelText("Running, waiting to Pause");
                refreshButtonAvailability();
            }
            @Override
            public void threadRunningWaitingToStop() {
                t.setStateLabelText("Running, waiting to Stop");
                refreshButtonAvailability();
            }
            @Override
            public void threadStoppedNormally() {
                t.setStateLabelText("Stopped normally");
                refreshButtonAvailability();
            }
            @Override
            public void threadRestarted() {
                t.setStateLabelText("Stopped after restart request");
                refreshButtonAvailability();
            }
        });
        jf.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        refreshButtonAvailability();
        jf.setVisible(true);
    }
    
    JLabel currentNum = new JLabel();
    void setCurrentValue(int i) {
        currentNum.setText(String.valueOf(i));
    }
    
    JLabel state = new JLabel();
    void setStateLabelText(String stateText) {
        state.setText(stateText);
    }
    
    static void refreshButtonAvailability() {
        buttonRestart.setEnabled(t.canRestart());
        buttonStart.setEnabled(t.canStart());
        buttonStop.setEnabled(t.canStop());
        buttonPause.setEnabled(t.canPause());
        buttonResume.setEnabled(t.canResume());
    }

    static class ButtonRestart extends JButton {
        final MyRichThread thread;
        public ButtonRestart(MyRichThread t) {
            this.setText("Restart");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        thread.restart();
                    } catch (OperationNotAllowedException e1) {
                        //here, if you want/need, notify the user that it's not allowed
                    	//in our example we cannot reach this code, since buttons are 
                    	//grayed out so it's impossible to trigger uncompatible actions
                        e1.printStackTrace();
                    }
                }
            });
        }
    }
    static class ButtonStart extends JButton {
        final MyRichThread thread;
        public ButtonStart(MyRichThread t) {
            this.setText("Start");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        thread.start();
                    } catch (OperationNotAllowedException e1) {
                        //here, if you want/need, notify the user that it's not allowed
                    	//in our example we cannot reach this code, since buttons are 
                    	//grayed out so it's impossible to trigger uncompatible actions
                        e1.printStackTrace();
                    }
                }
            });
        }
    }
    static class ButtonStop extends JButton {
        final MyRichThread thread;
        public ButtonStop(MyRichThread t) {
            this.setText("Stop");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        thread.stop();
                    } catch (OperationNotAllowedException e1) {
                        //here, if you want/need, notify the user that it's not allowed
                    	//in our example we cannot reach this code, since buttons are 
                    	//grayed out so it's impossible to trigger uncompatible actions
                        e1.printStackTrace();
                    }
                }
            });
        }
    }
    static class ButtonPause extends JButton {
        final MyRichThread thread;
        public ButtonPause(MyRichThread t) {
            this.setText("Pause");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        thread.pause();
                    } catch (OperationNotAllowedException e1) {
                        //here, if you want/need, notify the user that it's not allowed
                    	//in our example we cannot reach this code, since buttons are 
                    	//grayed out so it's impossible to trigger uncompatible actions
                        e1.printStackTrace();
                    }
                }
            });
        }
    }
    static class ButtonResume extends JButton {
        final MyRichThread thread;
        public ButtonResume(MyRichThread t) {
            this.setText("Resume");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        thread.resume();
                    } catch (OperationNotAllowedException e1) {
                        //here, if you want/need, notify the user that it's not allowed
                    	//in our example we cannot reach this code, since buttons are 
                    	//grayed out so it's impossible to trigger uncompatible actions
                        e1.printStackTrace();
                    }
                }
            });
        }
    }
    static class ButtonNormalTermination extends JButton {
        final MyRichThread thread;
        public ButtonNormalTermination(MyRichThread t) {
            this.setText("Simulate termination");
            this.setToolTipText("Simulate a normal thread termination after all the work is done");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doTerminateNormally = true;
                }
            });
        }
    }
    static class ButtonGenerateException extends JButton {
        final MyRichThread thread;
        public ButtonGenerateException(MyRichThread t) {
            this.setText("Simulate a runtime exception");
            this.thread = t;
            this.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doGenerateException = true;
                }
            });
        }
    }
}
