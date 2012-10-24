package com.gryzorz.thread.richthread.v1;

import java.util.ArrayList;
import java.util.List;

import com.gryzorz.fsm.v2.Event;
import com.gryzorz.fsm.v2.FiniteStateMachine;
import com.gryzorz.fsm.v2.FiniteStateMachineBuilder;
import com.gryzorz.fsm.v2.FiniteStateMachineException;
import com.gryzorz.fsm.v2.FiniteStateMachineListener;
import com.gryzorz.fsm.v2.State;
import com.gryzorz.fsm.v2.TransitionNotAllowedException;

/**
 * The RichThread's class goal is to provide a much more convenient way to
 * handle threads that can be paused or stopped in Java.<br>
 * The equivalent of the method run() of standard Java thread is execute(),
 * which has to be overridden exactly the same way as run() for a standard
 * Thread.<br>
 * The RichThread is also started simply with a start() call.<br>
 * There are two methods : stop() and pause() that can be called on the
 * RichThread.<br>
 * Each of these methods will stop or pause the Thread when, during the
 * execution, a "stopBreakpoint" or a "pauseBreakpoint" is encountered.<br>
 * The two methods setStopBreakpoint() and setPauseBreakpoint() are here to be
 * used inside of method execute() to set those breakpoints.<br>
 * <br>
 * You can also add listeners to the RichThread behaviour adding as many
 * DefaultRichThreadListener as you want (method addListener(...)). Those
 * listeners will help you know whenever an action occurs that make the
 * RichThread change state.<br>
 * 
 * @author Benoit Fernandez
 */
public abstract class RichThread {

	/**************************************************************************
	 * execution thread implementation *
	 *************************************************************************/
	/* the effective Thread that will handle the code execution */
	private Thread executionThread;
	private Object threadMonitor = new Object();

	/* for debugging convenience, you can name your thread */
	private String name = "";

	/*
	 * this finite state machine records the current state of the execution
	 * thread
	 */
	private FiniteStateMachine executionThreadStateMachine;

	/* list of possible states for the execution thread */
	private static final State STOPPED = new State("STOPPED");
	private static final State RUNNING = new State("RUNNING");
	private static final State RUNNING_WAITING_FOR_PAUSE = new State(
			"RUNNING_WAITING_FOR_PAUSE");
	private static final State RUNNING_WAITING_FOR_STOP = new State(
			"RUNNING_WAITING_FOR_STOP");
	private static final State STOPPING = new State("STOPPING");
	private static final State PAUSED = new State("PAUSED");
	private static final State CRASHED = new State("CRASHED");

	private Throwable crashException; /*
									 * to store exception or error that caused
									 * the thread crash
									 */

	/* list of possible events */
	private static final Event EVENT_START = new Event("START");
	private static final Event EVENT_TERMINATED = new Event("TERMINATED");
	private static final Event EVENT_STOP = new Event("STOP");
	private static final Event EVENT_PAUSE = new Event("PAUSE");
	private static final Event EVENT_RESUME = new Event("RESUME");
	private static final Event EVENT_STOP_BREAKPOINT_ENCOUNTERED = new Event(
			"STOP_BREAKPOINT_ENCOUNTERED");
	private static final Event EVENT_PAUSE_BREAKPOINT_ENCOUNTERED = new Event(
			"PAUSE_BREAKPOINT_ENCOUNTERED");
	private static final Event EVENT_CRASH = new Event("CRASH");
	private static final Event EVENT_RESTART = new Event("RESTART");

	/*
	 * This method creates a finite state machine that is the key of the correct
	 * behaviour of the java Thread used to really do the job (the
	 * executionThread declared on top)
	 */
	private void createExecutionThreadStateMachine() {
		FiniteStateMachineBuilder executionThreadFSMBuilder = new FiniteStateMachineBuilder();
		try {
			executionThreadFSMBuilder.addTransition(STOPPED, EVENT_START,
					RUNNING);
			executionThreadFSMBuilder.addTransition(RUNNING, EVENT_STOP,
					RUNNING_WAITING_FOR_STOP);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_STOP,
					EVENT_START, RUNNING);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_STOP,
					EVENT_STOP_BREAKPOINT_ENCOUNTERED, STOPPING);
			executionThreadFSMBuilder.addTransition(STOPPING, EVENT_TERMINATED,
					STOPPED);

			executionThreadFSMBuilder.addTransition(PAUSED, EVENT_RESUME,
					RUNNING);
			executionThreadFSMBuilder.addTransition(RUNNING, EVENT_PAUSE,
					RUNNING_WAITING_FOR_PAUSE);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_PAUSE,
					EVENT_RESUME, RUNNING);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_PAUSE,
					EVENT_PAUSE_BREAKPOINT_ENCOUNTERED, PAUSED);

			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_STOP,
					EVENT_PAUSE, RUNNING_WAITING_FOR_PAUSE);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_PAUSE,
					EVENT_STOP, RUNNING_WAITING_FOR_STOP);

			executionThreadFSMBuilder.addTransition(RUNNING, EVENT_CRASH,
					CRASHED);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_PAUSE,
					EVENT_CRASH, CRASHED);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_STOP,
					EVENT_CRASH, CRASHED);
			executionThreadFSMBuilder.addTransition(STOPPING, EVENT_CRASH,
					CRASHED);

			executionThreadFSMBuilder.addTransition(CRASHED, EVENT_RESTART,
					STOPPED);

			executionThreadFSMBuilder.addTransition(RUNNING, EVENT_TERMINATED,
					STOPPED);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_STOP,
					EVENT_TERMINATED, STOPPED);
			executionThreadFSMBuilder.addTransition(RUNNING_WAITING_FOR_PAUSE,
					EVENT_TERMINATED, STOPPED);
			/* finally create the finite state machine */
			executionThreadStateMachine = executionThreadFSMBuilder
					.createFSM(STOPPED);

		} catch (FiniteStateMachineException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	/**************************************************************************
	 * accessors *
	 *************************************************************************/
	public boolean isStopped() {
		return STOPPED.equals(getCurrentState());
	}

	public boolean isRunning() {
		return RUNNING.equals(getCurrentState());
	}

	public boolean isRunningWaitingForPause() {
		return RUNNING_WAITING_FOR_PAUSE.equals(getCurrentState());
	}

	public boolean isRunningWaitingForStop() {
		return RUNNING_WAITING_FOR_STOP.equals(getCurrentState());
	}

	public boolean isStopping() {
		return STOPPING.equals(getCurrentState());
	}

	public boolean isPaused() {
		return PAUSED.equals(getCurrentState());
	}

	public boolean isCrashed() {
		return CRASHED.equals(getCurrentState());
	}

	public Throwable getLastCrashException() {
		return crashException;
	}

	/**************************************************************************
	 * listener part *
	 *************************************************************************/
	/**
	 * Use the listeners to be informed of state change of the thread
	 */
	List<RichThreadListener> listeners = new ArrayList<RichThreadListener>();

	/**
	 * @param listener
	 *            the listener that will listen to the thread state changes.<br>
	 *            use DefaultRichThreadListener if you do not want to implement
	 *            all the methods
	 */
	public final void addListener(RichThreadListener listener) {
		listeners.add(listener);
	}

	/**
	 * @param listener
	 *            the listener to be removed
	 */
	public final void removeListener(RichThreadListener listener) {
		listeners.remove(listener);
	}

	protected final void notifyThreadRunning() {
		for (RichThreadListener listener : listeners) {
			listener.threadRunning();
		}
	}

	protected final void notifyThreadRunningWaitingToPause() {
		for (RichThreadListener listener : listeners) {
			listener.threadRunningWaitingToPause();
		}
	}

	protected final void notifyThreadRunningWaitingToStop() {
		for (RichThreadListener listener : listeners) {
			listener.threadRunningWaitingToStop();
		}
	}

	protected final void notifyThreadStopping() {
		for (RichThreadListener listener : listeners) {
			listener.threadStopping();
		}
	}

	protected final void notifyThreadStoppedAfterStopRequest() {
		for (RichThreadListener listener : listeners) {
			listener.threadStoppedAfterStopRequest();
		}
	}

	protected final void notifyThreadStoppedNormally() {
		for (RichThreadListener listener : listeners) {
			listener.threadStoppedNormally();
		}
	}

	protected final void notifyThreadRestarted() {
		for (RichThreadListener listener : listeners) {
			listener.threadRestarted();
		}
	}

	protected final void notifyThreadPaused() {
		for (RichThreadListener listener : listeners) {
			listener.threadPaused();
		}
	}

	protected final void notifyThreadCrashed(Throwable t) {
		for (RichThreadListener listener : listeners) {
			listener.threadCrashed(t);
		}
	}

	/**************************************************************************
	 * constructor *
	 *************************************************************************/
	/**
	 * For debugging convenience, you'd better use constructor RichThread(String
	 * name) and use getName() to retrieve it
	 */
	public RichThread() {
		createExecutionThreadStateMachine();
		executionThreadStateMachine
				.addListener(new FiniteStateMachineListener() {

					@Override
					public void unexistingTransition(State from, Event event) {
					}

					@Override
					public void stateLoop(State state, Event event) {
					}

					@Override
					/**
					 * We forward state changes to the outside, where different methods are called
					 * depending on the new state we are in.
					 * Curiously, the implementation is not based on a java Thread, it's based on a state machine.
					 * The state machine have different static states, which were identified and created to match
					 * the needs of a stoppable and pauseable thread, and we wrap the change listeners of the
					 * state machine to static listeners matching the needs of such a thread.<br>
					 * We also use the state change to control the usage of the java Thread that really executes
					 * the code inside method execute().
					 */
					public void stateChanged(State from, Event event, State to) {
						if (STOPPED == to) {
							/*
							 * code is in method setStopBreakpoint(), because
							 * it's the one directly executed through the thread
							 */
							if (STOPPING == from) {
								notifyThreadStoppedAfterStopRequest();
							} else if (CRASHED == from) {
								notifyThreadRestarted();
							} else {
								notifyThreadStoppedNormally();
							}
						} else if (RUNNING == to) {
							try {
								if (CRASHED == from || STOPPED == from) {
									executionThread = new Thread() {
										@Override
										public void run() {
											try {
												execute();
												executionThreadStateMachine
														.processEventWithoutErrorNotification(EVENT_TERMINATED);
											} catch (ExecutionStoppedException e) {
												/*
												 * nothing, it just means that
												 * the exception was forwarded,
												 * most likely because there was
												 * no cleanup necessary
												 */
												executionThreadStateMachine
														.processEventWithoutErrorNotification(EVENT_TERMINATED);
											} catch (Throwable t) {
												crashException = t;
												executionThreadStateMachine
														.processEventWithoutErrorNotification(EVENT_CRASH);
											}
										}
									};
									executionThread.start();
								}
							} catch (IllegalThreadStateException e) {
								/* nothing - thread is already started, it's ok */
							}
							if (PAUSED == from) {
								synchronized (threadMonitor) {
									threadMonitor.notify(); /*
															 * in case it was
															 * paused
															 */
								}
							}
							notifyThreadRunning();

						} else if (PAUSED == to) {
							/*
							 * code is in method setPauseBreakpoint(), because
							 * it's the one directly executed through the thread
							 */
							notifyThreadPaused();
						} else if (CRASHED == to) {
							notifyThreadCrashed(crashException);
						} else if (RUNNING_WAITING_FOR_PAUSE == to) {
							notifyThreadRunningWaitingToPause();
						} else if (RUNNING_WAITING_FOR_STOP == to) {
							notifyThreadRunningWaitingToStop();
						} else if (STOPPING == to) {
							notifyThreadStopping();
						}
					}

					@Override
					public void eventOccurred(State from, Event event, State to) {
					}
				});
	}

	/**
	 * @param name
	 *            the name you want to give to your RichThread. You can retrieve
	 *            it with getName().<br>
	 *            Entering null will result in name = "".
	 */
	public RichThread(String name) {
		this();
		if (name != null) {
			this.name = name;
		}
	}

	/**************************************************************************
	 * public methods
	 *************************************************************************/

	/**
	 * 
	 * @throws OperationNotAllowedException
	 *             in case the operation is not allowed, though the thread won't
	 *             change behaviour
	 */
	public final void start() throws OperationNotAllowedException {
		try {
			executionThreadStateMachine.processEvent(EVENT_START);
		} catch (TransitionNotAllowedException e) {
			throw new OperationNotAllowedException(
					getName()
							+ " - RichThread could not be \"start()\" because it is currently in state "
							+ getCurrentState());
		}
	}

	public boolean canStart() {
		return executionThreadStateMachine.isTransitionExisting(EVENT_START);
	}

	public final void stop() throws OperationNotAllowedException {
		try {
			executionThreadStateMachine.processEvent(EVENT_STOP);
		} catch (TransitionNotAllowedException e) {
			throw new OperationNotAllowedException(
					getName()
							+ " - RichThread could not be \"stop()\" because it is currently in state "
							+ getCurrentState());
		}
	}

	public boolean canStop() {
		return executionThreadStateMachine.isTransitionExisting(EVENT_STOP);
	}

	public final void pause() throws OperationNotAllowedException {
		try {
			executionThreadStateMachine.processEvent(EVENT_PAUSE);
		} catch (TransitionNotAllowedException e) {
			throw new OperationNotAllowedException(
					getName()
							+ " - RichThread could not be \"pause()\" because it is currently in state "
							+ getCurrentState());
		}
	}

	public boolean canPause() {
		return executionThreadStateMachine.isTransitionExisting(EVENT_PAUSE);
	}

	public final void resume() throws OperationNotAllowedException {
		try {
			executionThreadStateMachine.processEvent(EVENT_RESUME);
		} catch (TransitionNotAllowedException e) {
			throw new OperationNotAllowedException(
					getName()
							+ " - RichThread could not be \"resume()\" because it is currently in state "
							+ getCurrentState());
		}
	}

	public boolean canResume() {
		return executionThreadStateMachine.isTransitionExisting(EVENT_RESUME);
	}

	public final void restart() throws OperationNotAllowedException {
		try {
			executionThreadStateMachine.processEvent(EVENT_RESTART);
		} catch (TransitionNotAllowedException e) {
			throw new OperationNotAllowedException(
					getName()
							+ " - RichThread could not be \"restart()\" because it is currently in state "
							+ getCurrentState());
		}
	}

	public boolean canRestart() {
		return executionThreadStateMachine.isTransitionExisting(EVENT_RESTART);
	}

	/** accessors */
	public State getCurrentState() {
		return executionThreadStateMachine.getState();
	}

	/**
	 * Get the Id of the thread
	 * 
	 * @return
	 */
	public Long getId() {
		if (executionThread != null) {
			return executionThread.getId();
		}

		return null;
	}

	/**
	 * @return the name you gave to your RichThread using constructor
	 *         RichThread(String name), or "" if you didnt.
	 */
	public String getName() {
		return name;
	}

	/**************************************************************************
	 * protected methods *
	 *************************************************************************/
	/**
	 * Calling this method inside body of method execute() can have two
	 * different effects :<br>
	 * 1) either continuing without doing anything<br>
	 * or, in case a call to method pause() was made, it will<br>
	 * 2) suspend the execution of the thread until method resume() is called<br>
	 */
	protected final void setPauseBreakpoint() {
		executionThreadStateMachine
				.processEventWithoutErrorNotification(EVENT_PAUSE_BREAKPOINT_ENCOUNTERED);
		if (PAUSED == executionThreadStateMachine.getState()) {
			if (Thread.currentThread() == executionThread) { /*
															 * this method must
															 * only be called
															 * inside of an
															 * inherited
															 * "execute()"
															 * method
															 */
				synchronized (threadMonitor) {
					try {
						threadMonitor.wait();
					} catch (InterruptedException e) {
						/*
						 * will never happen since we never call
						 * executionThread.interrupt(), and nobody can
						 */
					}
				}
			} else {
				throw new RuntimeException(
						"call of method \"setPauseBreakpoint\" of class RichThread is strictly prohibited outside of method \"execute\"");
			}
		}
	}

	/**
	 * This method should be used to indicate, during the execution of method
	 * execute() in the separated thread, that you allow your
	 * 
	 * @throws ExecutionStoppedException
	 */
	protected final void setStopBreakpoint() throws ExecutionStoppedException {
		executionThreadStateMachine
				.processEventWithoutErrorNotification(EVENT_STOP_BREAKPOINT_ENCOUNTERED);
		if (STOPPING == executionThreadStateMachine.getState()) {
			if (Thread.currentThread() == executionThread) { /*
															 * this method must
															 * only be called
															 * inside of an
															 * inherited
															 * "execute()"
															 * method
															 */
				throw new ExecutionStoppedException();
			}
			/* if method called from outside of method "execute" -> exception ! */
			throw new RuntimeException(
					"call of method \"setStopBreakpoint\" of class RichThread is strictly prohibited outside of method \"execute\"");

		}
	}

	/**************************************************************************
	 * method that will be executed by thread *
	 *************************************************************************/
	/**
	 * Subclasses must override this method in order to execute the code they
	 * want.<br>
	 * The code executed must provide as much breakpoints for possible pauses or
	 * stops in order to take benefits from this class.<br>
	 * You can handle Stop exceptions in two different ways, depending on your
	 * needs :<br>
	 * - either you need to do some cleanup and you surround all the code inside
	 * the execute() method with a try/catch capturing ExecutionStoppedException
	 * where you will free ressources<br>
	 * - either you dont need to do any cleanup, and just let the exception
	 * raised from method setStopBreakpoint() go back up the stack
	 */
	protected abstract void execute() throws Exception;
}
