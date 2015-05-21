package org.nypl.simplified.app;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.nypl.simplified.app.catalog.CatalogBookCoverGenerator;
import org.nypl.simplified.app.catalog.CatalogBookCoverGeneratorRequestHandler;
import org.nypl.simplified.app.catalog.CatalogBookCoverGeneratorType;
import org.nypl.simplified.app.utilities.LogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.core.BookID;
import org.nypl.simplified.books.core.BookSnapshot;
import org.nypl.simplified.books.core.BooksType;
import org.nypl.simplified.books.core.FeedEntryOPDS;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.slf4j.Logger;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

public final class BookCoverProvider implements BookCoverProviderType
{
  private static final Logger LOG;
  private static final String THUMBNAIL_TAG;

  static {
    LOG = LogUtilities.getLog(BookCoverProvider.class);
    THUMBNAIL_TAG = "thumbnail";
  }

  private static URI generateCoverURI(
    final FeedEntryOPDS e,
    final CatalogBookCoverGeneratorType cg)
  {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final String title = eo.getTitle();
    final String author;
    final List<String> authors = eo.getAuthors();
    if (authors.isEmpty()) {
      author = "";
    } else {
      author = NullCheck.notNull(authors.get(0));
    }
    return cg.generateURIForTitleAuthor(title, author);
  }

  public static BookCoverProviderType newCoverProvider(
    final Context in_c,
    final BooksType in_books,
    final ExecutorService in_exec)
  {
    final CatalogBookCoverGenerator cover_gen =
      new CatalogBookCoverGenerator();

    final Resources rr = in_c.getResources();
    final Picasso.Builder pb = new Picasso.Builder(in_c);
    pb.defaultBitmapConfig(Bitmap.Config.RGB_565);
    pb.indicatorsEnabled(true);
    pb.loggingEnabled(rr.getBoolean(R.bool.debug_picasso));
    pb.addRequestHandler(new CatalogBookCoverGeneratorRequestHandler(
      cover_gen));
    pb.executor(in_exec);

    final Picasso p = NullCheck.notNull(pb.build());
    return new BookCoverProvider(p, in_books, cover_gen);
  }

  private final BooksType                     books;
  private final CatalogBookCoverGeneratorType cover_gen;
  private final Picasso                       picasso;

  private BookCoverProvider(
    final Picasso in_p,
    final BooksType in_books,
    final CatalogBookCoverGeneratorType in_cover_gen)
  {
    this.picasso = NullCheck.notNull(in_p);
    this.books = NullCheck.notNull(in_books);
    this.cover_gen = NullCheck.notNull(in_cover_gen);
  }

  @Override public void loadCoverInto(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h)
  {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    this.loadCoverIntoActual(e, i, w, h, null);
  }

  private void loadCoverIntoActual(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h,
    final @Nullable Callback c)
  {
    BookCoverProvider.LOG.debug("{}: loadCoverInto", e.getBookID());

    UIThread.checkIsUIThread();

    final URI uri;
    final BookID id = e.getBookID();
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final OptionType<BookSnapshot> snap_opt = this.books.booksSnapshotGet(id);
    if (snap_opt.isSome()) {
      final BookSnapshot snap = ((Some<BookSnapshot>) snap_opt).get();

      /**
       * On receipt of a book snapshot, construct a URI from the location of
       * the cover image, if any. If there is no cover, generate one as
       * normal.
       */

      final OptionType<File> cover_opt = snap.getCover();
      if (cover_opt.isSome()) {
        final Some<File> some = (Some<File>) cover_opt;
        uri = some.get().toURI();
      } else {
        uri = BookCoverProvider.generateCoverURI(e, this.cover_gen);
      }

    } else {

      /**
       * If no snapshot is received, either fetch or generate a cover as with
       * ordinary books.
       */

      final OptionType<URI> cover_opt = eo.getCover();
      if (cover_opt.isSome()) {
        final Some<URI> some = (Some<URI>) cover_opt;
        uri = some.get();
      } else {
        uri = BookCoverProvider.generateCoverURI(e, this.cover_gen);
      }
    }

    BookCoverProvider.LOG.debug("{}: uri {}", e.getBookID(), uri);

    final RequestCreator r = this.picasso.load(uri.toString());
    r.resize(w, h);
    r.into(i, c);
  }

  @Override public void loadCoverIntoWithCallback(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h,
    final Callback c)
  {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    NullCheck.notNull(c);
    this.loadCoverIntoActual(e, i, w, h, c);
  }

  @Override public void loadingThumbailsPause()
  {
    this.picasso.pauseTag(BookCoverProvider.THUMBNAIL_TAG);
  }

  @Override public void loadingThumbnailsContinue()
  {
    this.picasso.resumeTag(BookCoverProvider.THUMBNAIL_TAG);
  }

  @Override public void loadThumbnailInto(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h)
  {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    this.loadThumbnailIntoActual(e, i, w, h, null);
  }

  private void loadThumbnailIntoActual(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h,
    final @Nullable Callback c)
  {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();

    BookCoverProvider.LOG.debug(
      "{}: loadThumbnailInto {}",
      e.getBookID(),
      eo.getID());

    UIThread.checkIsUIThread();

    final URI uri;
    final BookID id = e.getBookID();
    final OptionType<BookSnapshot> snap_opt = this.books.booksSnapshotGet(id);
    if (snap_opt.isSome()) {
      final BookSnapshot snap = ((Some<BookSnapshot>) snap_opt).get();

      /**
       * On receipt of a book snapshot, construct a URI from the location of
       * the cover image, if any. If there is no cover, generate one as
       * normal.
       */

      final OptionType<File> cover_opt = snap.getCover();
      if (cover_opt.isSome()) {
        final Some<File> some = (Some<File>) cover_opt;
        uri = some.get().toURI();
      } else {
        uri = BookCoverProvider.generateCoverURI(e, this.cover_gen);
      }

    } else {

      /**
       * If no snapshot is received, either fetch or generate a cover as with
       * ordinary books.
       */

      final OptionType<URI> thumb_opt = eo.getThumbnail();
      if (thumb_opt.isSome()) {
        final Some<URI> some = (Some<URI>) thumb_opt;
        uri = some.get();
      } else {
        uri = BookCoverProvider.generateCoverURI(e, this.cover_gen);
      }
    }

    BookCoverProvider.LOG.debug("{}: uri {}", e.getBookID(), uri);

    final RequestCreator r = this.picasso.load(uri.toString());
    r.tag(BookCoverProvider.THUMBNAIL_TAG);
    r.resize(w, h);
    r.into(i, c);
  }

  @Override public void loadThumbnailIntoWithCallback(
    final FeedEntryOPDS e,
    final ImageView i,
    final int w,
    final int h,
    final Callback c)
  {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    NullCheck.notNull(c);
    this.loadThumbnailIntoActual(e, i, w, h, c);
  }
}