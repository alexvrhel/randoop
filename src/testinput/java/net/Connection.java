package net;

/**
 * Note: this file is a slighly modified version of the tutorial file for Toradocu, and was used to
 * generate the {@code net_Connection} class at the top level of the testinput source set.
 * If this class is modified, it should be run through Toradocu again to generate that input file
 * again.
 */
public class Connection {

  private boolean open = false;

  /** @returns true if the connection is open, false otherwise */
  public boolean isOpen() {
    return open;
  }

  /**
   * Establishes a new connection.
   *
   * @throws IllegalStateException if the connection is already open
   */
  public void open() {
    // Establish a new connection.

    // ... code elided ...

    open = true;
  }

  /**
   * Sends a message.
   *
   * @throws NullPointerException if message is null
   * @throws IllegalStateException if the connection is not open
   */
  public void send(String message) {
    // Optimization: no action if message is empty.
    if (message.isEmpty()) {
      return;
    }

    // Send the message.

    // ... code elided ...
  }

  /**
  * @param code  the code, must be positive
  * @throws IllegalStateException if the connection is not open
  */
  public void send(int code) {
    if (!this.isOpen()) {
      throw new IllegalStateException();
    }
  }
}
