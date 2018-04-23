package org.nypl.simplified.app.reader

import android.content.Context
import android.database.DataSetObserver
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

import org.nypl.simplified.app.R
import org.nypl.simplified.app.Simplified
import org.nypl.simplified.books.core.BookmarkAnnotation

/**
 * A reusable fragment for a ListView of bookmarks
 */

class ReaderTOCBookmarksFragment : Fragment(), ListAdapter {

  private var inflater: LayoutInflater? = null
  private var adapter: ArrayAdapter<BookmarkAnnotation>? = null
  private var listener: ReaderTOCBookmarksFragmentSelectionListenerType? = null

  private var bookmarksTOCLayout: View? = null
  private var bookmarksTOCListView: ListView? = null


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {

    this.inflater = inflater
    bookmarksTOCLayout = inflater.inflate(R.layout.reader_toc_bookmarks, null)
    bookmarksTOCListView = bookmarksTOCLayout?.findViewById(R.id.bookmarks_list) as? ListView

    val activity = activity as? ReaderTOCActivity

    //TODO WIP
    val bookmarks = activity?.bookmarks ?: ArrayList<BookmarkAnnotation>(0)

    adapter = ArrayAdapter(context, 0, bookmarks)
    bookmarksTOCListView?.adapter = this

    return bookmarksTOCLayout
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (context is ReaderTOCBookmarksFragmentSelectionListenerType) {
      listener = context
    } else {
      throw RuntimeException(context.toString() +
          " must implement ReaderTOCBookmarksFragmentSelectionListenerType")
    }
  }

  /**
   * List View Adapter
   */

  override fun areAllItemsEnabled(): Boolean {
    return adapter!!.areAllItemsEnabled()
  }

  override fun getCount(): Int {
    return adapter!!.count
  }

  override fun getItem(position: Int): Any {
    return adapter!!.getItem(position)
  }

  override fun getItemId(position: Int): Long {
    return adapter!!.getItemId(position)
  }

  override fun getItemViewType(position: Int): Int {
    return adapter!!.getItemViewType(position)
  }

  override fun getView(position: Int, reuse: View?, parent: ViewGroup?): View {

    val itemView = if (reuse != null) {
      reuse as ViewGroup
    } else {
      inflater?.inflate(R.layout.reader_toc_element, parent, false) as ViewGroup
    }

    val layoutView = itemView.findViewById<ViewGroup>(R.id.toc_bookmark_element)
    val textView = layoutView.findViewById<TextView>(R.id.toc_bookmark_element_title)
    val detailTextView = layoutView.findViewById<TextView>(R.id.toc_bookmark_element_subtitle)
    val bookmark = adapter?.getItem(position)

    val shortDate = if (bookmark?.body?.timestamp != null) {
      bookmark.body.timestamp + " - "
    } else { "" }
    val chapterProgress = if (bookmark?.body?.chapterProgress != null) {
      bookmark.body.chapterProgress.toString() + " through chapter"
    } else { "Chapter Location Marked" }

    val detailText = shortDate + chapterProgress

    textView.text = bookmark?.body?.chapterTitle ?: "Bookmark"
    detailTextView.text = detailText

    val rs = Simplified.getReaderAppServices()
    val settings = rs.settings

    textView.setTextColor(settings.colorScheme.foregroundColor)

    itemView.setOnClickListener { _ ->
      this.listener?.onBookmarkSelected(bookmark)
    }

    return layoutView
  }

  override fun getViewTypeCount(): Int {
    return adapter!!.viewTypeCount
  }

  override fun hasStableIds(): Boolean {
    return adapter!!.hasStableIds()
  }

  override fun isEmpty(): Boolean {
    return adapter!!.isEmpty
  }

  override fun isEnabled(position: Int): Boolean {
    return adapter!!.isEnabled(position)
  }

  override fun registerDataSetObserver(observer: DataSetObserver?) {
    adapter!!.registerDataSetObserver(observer)
  }

  override fun unregisterDataSetObserver(observer: DataSetObserver?) {
    adapter!!.unregisterDataSetObserver(observer)
  }
}

