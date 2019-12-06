package org.nypl.simplified.ui.catalog

import android.app.Activity
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.navigation.api.NavigationControllerType
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

/**
 * Navigation functions for the catalog screens.
 */

interface CatalogNavigationControllerType : NavigationControllerType {

  /**
   * The catalog wants to open a book detail page.
   */

  fun openBookDetail(entry: FeedEntryOPDS)

  /**
   * A catalog screen wants to open the error page.
   */

  fun <E : PresentableErrorType> openErrorPage(
    parameters: ErrorPageParameters<E>)

  /**
   * A catalog screen wants to open a feed.
   */

  fun openFeed(feedArguments: CatalogFeedArguments)

  /**
   * A catalog screen wants to open a viewer for a book
   */

  fun openViewer(
    activity: Activity,
    book: Book,
    format: BookFormat
  )

  /**
   * Clear the history for the catalog.
   */

  fun clearHistory()
}