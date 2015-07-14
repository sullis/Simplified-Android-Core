package org.nypl.simplified.books.core;

import java.util.Calendar;

import com.io7m.jfunctional.OptionType;
import com.io7m.jnull.NullCheck;

/**
 * The given book failed to download properly.
 */

public final class BookStatusDownloadFailed implements
  BookStatusDownloadingType
{
  private final OptionType<Throwable> error;
  private final BookID                id;
  private final OptionType<Calendar>  loan_end_date;

  public BookStatusDownloadFailed(
    final BookID in_id,
    final OptionType<Throwable> x,
    final OptionType<Calendar> in_loan_end_date)
  {
    this.id = NullCheck.notNull(in_id);
    this.error = NullCheck.notNull(x);
    this.loan_end_date = NullCheck.notNull(in_loan_end_date);
  }

  @Override public BookID getID()
  {
    return this.id;
  }

  @Override public OptionType<Calendar> getLoanExpiryDate()
  {
    return this.loan_end_date;
  }

  @Override public <A, E extends Exception> A matchBookDownloadingStatus(
    final BookStatusDownloadingMatcherType<A, E> m)
    throws E
  {
    return m.onBookStatusDownloadFailed(this);
  }

  @Override public <A, E extends Exception> A matchBookLoanedStatus(
    final BookStatusLoanedMatcherType<A, E> m)
    throws E
  {
    return m.onBookStatusDownloading(this);
  }

  @Override public <A, E extends Exception> A matchBookStatus(
    final BookStatusMatcherType<A, E> m)
    throws E
  {
    return m.onBookStatusLoanedType(this);
  }

  @Override public String toString()
  {
    final StringBuilder b = new StringBuilder();
    b.append("[BookStatusDownloadFailed ");
    b.append(this.id);
    b.append(" ");
    b.append(this.error);
    b.append("]");
    return NullCheck.notNull(b.toString());
  }

  @Override public BookStatusPriorityOrdering getPriority()
  {
    return BookStatusPriorityOrdering.BOOK_STATUS_DOWNLOAD_FAILED;
  }
}
