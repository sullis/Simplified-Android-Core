package org.nypl.simplified.books.core;

/**
 * <p>
 * An enum representing the possible types of book status, to make it slightly
 * easier to place an ordering on the priorities of status values.
 * </p>
 * <p>
 * The reason that this is necessary is because status updates may be received
 * in an arbitrary order and, in particular, late. The system needs to be able
 * to determine if a status update is important enough to override an existing
 * status update that was received out of order. The numeric priority values
 * are arbitrary, meant only to provide a partial order, and are subject to
 * change.
 * </p>
 */

public enum BookStatusPriorityOrdering
{
  BOOK_STATUS_DOWNLOAD_FAILED(90),
  BOOK_STATUS_DOWNLOAD_IN_PROGRESS(60),
  BOOK_STATUS_DOWNLOAD_REQUESTING(50),
  BOOK_STATUS_DOWNLOADED(100),
  BOOK_STATUS_LOANABLE(15),
  BOOK_STATUS_HELD(10),
  BOOK_STATUS_HOLDABLE(0),
  BOOK_STATUS_LOAN_IN_PROGRESS(20),
  BOOK_STATUS_LOANED(30);

  public int getPriority()
  {
    return this.priority;
  }

  private int priority;

  private BookStatusPriorityOrdering(
    final int in_priority)
  {
    this.priority = in_priority;
  }
}
