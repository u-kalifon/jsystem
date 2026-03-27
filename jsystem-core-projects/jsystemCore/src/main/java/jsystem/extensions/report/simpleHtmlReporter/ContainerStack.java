package jsystem.extensions.report.simpleHtmlReporter;

import java.util.ArrayDeque;
import java.util.Deque;

import jsystem.extensions.report.simpleHtmlReporter.dto.Status;

/**
 * Manages a stack of Container instances for hierarchical container and log level tracking.
 */
class ContainerStack {
	// this tracks the child scenarios, and the log levels within them
	private final Deque<Container> stack = new ArrayDeque<>();

	/**
	 * Returns the number of containers currently in the stack.
	 */
	public int size() {
		return stack.size();
	}

	/**
	 * Returns true if the stack is empty.
	 */
	public boolean isEmpty() {
		return stack.isEmpty();
	}

	/**
	 * Returns the container at the top of the stack without removing it.
	 * 
	 * @return the top container, or null if the stack is empty
	 */
	public Container peek() {
		return stack.peek();
	}

	/**
	 * Creates a new Container with the given name and pushes it onto the stack.
	 * 
	 * @param name the name of the container
	 */
	public void push(String name) {
		stack.push(new Container(name));
	}

	/**
	 * Removes and returns the container at the top of the stack.
	 * 
	 * @return the removed container, or null if the stack is empty
	 */
	public Container pop() {
		Container popped = stack.poll();
		if (popped != null && !stack.isEmpty()) {
			// update the containing container's status to the popped container's status
			stack.peek().setStatus(popped.getStatus());
		}
		return popped;
	}

	/**
	 * Represents a container with a name and its own stack of log levels.
	 */
	static class Container {
		private final String name;
		private Status status;
		private final Deque<LogLevel> logLevels;

		/**
		 * Creates a new Container with the given name and an empty log levels stack.
		 * 
		 * @param name the name of the container
		 */
		public Container(String name) {
			this.name = name;
			this.logLevels = new ArrayDeque<>();
			this.status = Status.RUNNING;
		}

		/**
		 * Returns the name of this container.
		 */
		public String getName() {
			return name;
		}

		public void startLevel(String name) {
			logLevels.push(new LogLevel(name));
		}

		public Status endLevel() {
			if (logLevels.isEmpty()) {
				return null;
			}
			Status status = logLevels.pop().getStatus();
			// update the encompassing level's status to the popped level's status
			if (!logLevels.isEmpty()) {
				logLevels.peek().setStatus(status);
			}
			return status;
		}

		public String getCurrentLevelName() {
			if (logLevels.isEmpty()) {
				return null;
			}
			return logLevels.peek().getName();
		}

		public int getLogLevelSize() {
			return logLevels.size();
		}

		public Status getStatus() {
			return status;
		}

		/**
		 * Sets the status of this container.
		 * This also ensures that the status can only get more severe, and cannot be set lower
		 * (e.g. FAILURE cannot be set to SUCCESS)
		 * 
		 * @param newStatus the new status
		 */
		public void setStatus(Status newStatus) {
			this.status = this.status.updateStatus(newStatus);
			// update the current level's status to the new status
			if (!logLevels.isEmpty()) {
				logLevels.peek().setStatus(newStatus);
			}
		}
	}

	/**
	 * Represents a log level with a name and status.
	 */
	static class LogLevel {
		private final String name;
		private Status status;

		/**
		 * Creates a new LogLevel with the given name and default status of SUCCESS.
		 * 
		 * @param name the name of the log level
		 */
		public LogLevel(String name) {
			this.name = name;
			this.status = Status.SUCCESS;
		}

		/**
		 * Returns the name of this log level.
		 */
		public String getName() {
			return name;
		}

		/**
		 * Returns the status of this log level.
		 */
		public Status getStatus() {
			return status;
		}

		/**
		 * Sets the status of this log level.
		 * This also ensures that the status can only get more severe, and cannot be set lower
		 * (e.g. FAILURE cannot be set to SUCCESS)
		 * 
		 * @param newStatus the new status
		 */
		public void setStatus(Status newStatus) {
			this.status = this.status.updateStatus(newStatus);
		}
	}
}

