package com.timehop.stickyheadersrecyclerview.decorators;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;

import com.timehop.stickyheadersrecyclerview.HeaderPositionCalculator;
import com.timehop.stickyheadersrecyclerview.ItemVisibilityAdapter;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersPositionChangeListener;
import com.timehop.stickyheadersrecyclerview.caching.HeaderProvider;
import com.timehop.stickyheadersrecyclerview.caching.HeaderViewCache;
import com.timehop.stickyheadersrecyclerview.calculation.DimensionCalculator;
import com.timehop.stickyheadersrecyclerview.rendering.HeaderRenderer;
import com.timehop.stickyheadersrecyclerview.util.LayoutManagerOrientationProvider;
import com.timehop.stickyheadersrecyclerview.util.OrientationProvider;

public class StickyRecyclerHeadersDecoration extends RecyclerView.ItemDecoration {

  private final StickyRecyclerHeadersAdapter mAdapter;
  private final SparseArray<Rect> mHeaderRects = new SparseArray<>();
  private final HeaderProvider mHeaderProvider;
  private final OrientationProvider mOrientationProvider;
  private final HeaderPositionCalculator mHeaderPositionCalculator;
  private final HeaderRenderer mRenderer;
  private final DimensionCalculator mDimensionCalculator;
  private final boolean mEnableStickyHeader;

  private ItemVisibilityAdapter mVisibilityAdapter;
  private StickyRecyclerHeadersPositionChangeListener mHeaderListener;

  /**
   * The following field is used as a buffer for internal calculations. Its sole purpose is to avoid
   * allocating new Rect every time we need one.
   */
  private final Rect mTempRect = new Rect();

  public StickyRecyclerHeadersDecoration(StickyRecyclerHeadersAdapter adapter) {
    this(adapter, new LayoutManagerOrientationProvider(), new DimensionCalculator(), true);
  }

  public StickyRecyclerHeadersDecoration(StickyRecyclerHeadersAdapter adapter, boolean enableStickyHeader) {
    this(adapter, new LayoutManagerOrientationProvider(), new DimensionCalculator(), enableStickyHeader);
  }

  private StickyRecyclerHeadersDecoration(StickyRecyclerHeadersAdapter adapter, OrientationProvider orientationProvider, DimensionCalculator dimensionCalculator, boolean enableStickyHeader) {
    this(adapter, orientationProvider, dimensionCalculator, new HeaderRenderer(orientationProvider), new HeaderViewCache(adapter, orientationProvider), enableStickyHeader);
  }

  private StickyRecyclerHeadersDecoration(StickyRecyclerHeadersAdapter adapter, OrientationProvider orientationProvider, DimensionCalculator dimensionCalculator, HeaderRenderer headerRenderer, HeaderProvider headerProvider, boolean enableStickyHeader) {
    this(adapter, headerRenderer, orientationProvider, dimensionCalculator, headerProvider, new HeaderPositionCalculator(adapter, headerProvider, orientationProvider, dimensionCalculator), enableStickyHeader);
  }

  private StickyRecyclerHeadersDecoration(StickyRecyclerHeadersAdapter adapter, HeaderRenderer headerRenderer, OrientationProvider orientationProvider, DimensionCalculator dimensionCalculator, HeaderProvider headerProvider, HeaderPositionCalculator headerPositionCalculator, boolean enableStickyHeader) {
    mAdapter = adapter;
    mHeaderProvider = headerProvider;
    mOrientationProvider = orientationProvider;
    mRenderer = headerRenderer;
    mDimensionCalculator = dimensionCalculator;
    mHeaderPositionCalculator = headerPositionCalculator;
    mEnableStickyHeader = enableStickyHeader;
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
    super.getItemOffsets(outRect, view, parent, state);
    int itemPosition = parent.getChildAdapterPosition(view);

    if (itemPosition != RecyclerView.NO_POSITION) {
      boolean hasNewHeader = mHeaderPositionCalculator.hasNewHeader(itemPosition, mOrientationProvider.isReverseLayout(parent));

      if (hasNewHeader) {
        View header = getHeaderView(parent, itemPosition);
        setItemOffsetsForHeader(outRect, header, mOrientationProvider.getOrientation(parent));
      }
    }
  }

  /**
   * Sets the offsets for the first item in a section to make room for the header view
   *
   * @param itemOffsets rectangle to define offsets for the item
   * @param header      view used to calculate offset for the item
   * @param orientation used to calculate offset for the item
   */
  private void setItemOffsetsForHeader(Rect itemOffsets, View header, int orientation) {
    mDimensionCalculator.initMargins(mTempRect, header);
    if (orientation == LinearLayoutManager.VERTICAL) {
      itemOffsets.top = header.getHeight() + mTempRect.top + mTempRect.bottom;
    } else {
      itemOffsets.left = header.getWidth() + mTempRect.left + mTempRect.right;
    }
  }

  @Override
  public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
    super.onDrawOver(canvas, parent, state);

    final int childCount = parent.getChildCount();
    if (childCount <= 0 || mAdapter.getItemCount() <= 0) {
      return;
    }

    for (int i = 0; i < childCount; i++) {
      View itemView = parent.getChildAt(i);

      int position = parent.getChildAdapterPosition(itemView);

      if (position == RecyclerView.NO_POSITION || position % (mAdapter.getNumColumns() - mAdapter.getSpanSize(position) + 1) > 0) {
        continue;
      }

      boolean hasStickyHeader = mHeaderPositionCalculator.hasStickyHeader(itemView, mOrientationProvider.getOrientation(parent), position);
      boolean hasNewHeader = mHeaderPositionCalculator.hasNewHeader(position, mOrientationProvider.isReverseLayout(parent));
      if (hasStickyHeader || hasNewHeader) {
        View header = mHeaderProvider.getHeader(parent, position);

        //re-use existing Rect, if any.
        Rect headerOffset = mHeaderRects.get(position);
        if (headerOffset == null) {
          mHeaderRects.put(position, headerOffset = new Rect());
        }

        mHeaderPositionCalculator.initHeaderBounds(headerOffset, parent, header, itemView, hasStickyHeader, mEnableStickyHeader);
        mRenderer.drawHeader(parent, canvas, header, headerOffset);

        if (mEnableStickyHeader && mHeaderListener != null) {
          mHeaderListener.onHeaderPositionChanged(this, mAdapter.getHeaderId(position), header, position, headerOffset);
        }
      }
    }
  }

  /**
   * Verify if header obscure some item on RecyclerView
   *
   * @param header The header to verify
   * @return first item that is fully beneath a header
   */
  public boolean headerObscuringSomeItem(RecyclerView recyclerView, View header) {
    return mHeaderPositionCalculator.headerObscuringSomeItem(recyclerView, header);
  }

  /**
   * Gets the position of the header under the specified (x, y) coordinates.
   *
   * @param x x-coordinate
   * @param y y-coordinate
   * @return position of header, or -1 if not found
   */
  public int findHeaderPositionUnder(int x, int y) {
    for (int i = 0; i < mHeaderRects.size(); i++) {
      Rect rect = mHeaderRects.get(mHeaderRects.keyAt(i));
      if (rect.contains(x, y)) {
        int position = mHeaderRects.keyAt(i);
        if (mVisibilityAdapter == null || mVisibilityAdapter.isPositionVisible(position)) {
          return position;
        }
      }
    }
    return -1;
  }

  /**
   * Gets the header view for the associated position.  If it doesn't exist yet, it will be
   * created, measured, and laid out.
   *
   * @param parent the recyclerview
   * @param position the position to get the header view for
   * @return Header view
   */
  public View getHeaderView(RecyclerView parent, int position) {
    return mHeaderProvider.getHeader(parent, position);
  }

  /**
   * Invalidates cached headers.  This does not invalidate the recyclerview, you should do that manually after
   * calling this method.
   */
  public void invalidateHeaders() {
    mHeaderProvider.invalidate();
    mHeaderRects.clear();
  }

  public void setVisibilityAdapter(ItemVisibilityAdapter visibilityAdapter) {
    this.mVisibilityAdapter = visibilityAdapter;
  }

  public void setHeaderPositionListener(StickyRecyclerHeadersPositionChangeListener headerListener) {
    this.mHeaderListener = headerListener;
  }

  public boolean isStickyHeadersEnabled() {
    return this.mEnableStickyHeader;
  }
}
