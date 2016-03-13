package edu.gac.mcs.max.piscroll;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Max Hailperin max@gustavus.edu on 3/12/16.
 * Known limitation: when the app is left with the back button and restarted, it starts over.
 */
public class PiScrollFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private TextAdapter mAdapter;
    private boolean mIsAutoScrolling;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pi_scroll, container, false);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.text_recycler_view);
        mLayoutManager = new LinearLayoutManager(getActivity(),
                LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                getActivity().setTitle(String.format(getString(R.string.title_format),
                        positionToDigit(mLayoutManager.findFirstVisibleItemPosition()),
                        positionToDigit(mLayoutManager.findLastVisibleItemPosition())));
            }

            // Digits are numbered from 1 rather than 0 and don't count the decimal point.
            private int positionToDigit(int position) {
                return position < 2 ? position + 1 : position;
            }
        });

        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            // As a stanadard UI feature, touching the RecyclerView stops any scroll that is in
            // progress (from a fling gesture, for example). Therefore, it seems to also make
            // sense to turn auto scrolling off (if it was on).
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                autoScrollOff();
                return false;
            }
        });

        if (mAdapter == null) {
            mAdapter = new TextAdapter();

            new Thread(new PiSpigot(new Writer() {
                @Override
                public void close() throws IOException {
                    // do nothing
                }

                @Override
                public void flush() throws IOException {
                    // do nothing
                }

                @Override
                public void write(@NonNull char[] buf, int offset, int count) throws IOException {
                    mAdapter.awaitDemand();
                    final String s = new String(buf, offset, count);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.append(s);
                            autoScrollIfEnabled();
                        }
                    });
                }
            })).start();
        }

        mRecyclerView.setAdapter(mAdapter);

        mRecyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                autoScrollIfEnabled();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main, menu);
        MenuItem item = menu.findItem(R.id.menu_item_auto_scroll);
        if (mIsAutoScrolling) {
            item.setTitle(R.string.manual_scroll);
            item.setIcon(R.drawable.ic_pause_24dp);
        } else {
            item.setTitle(R.string.auto_scroll);
            item.setIcon(R.drawable.ic_play_arrow_24dp);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_auto_scroll:
                mRecyclerView.stopScroll();
                mIsAutoScrolling = !mIsAutoScrolling;
                getActivity().invalidateOptionsMenu();
                autoScrollIfEnabled();
                return true;
            case R.id.menu_item_rewind:
                mRecyclerView.scrollToPosition(0);
                autoScrollOff();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void autoScrollOff() {
        mIsAutoScrolling = false;
        getActivity().invalidateOptionsMenu();
    }

    private void autoScrollIfEnabled() {
        if (mIsAutoScrolling && mRecyclerView.isAttachedToWindow()) {
            mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount() - 1);
        }
    }

    private class TextHolder extends RecyclerView.ViewHolder {

        private TextView mCharacterTextView;

        public TextHolder(View itemView) {
            super(itemView);
            mCharacterTextView = (TextView) itemView;
        }

        public void bindText(CharSequence text) {
            mCharacterTextView.setText(text);
        }
    }

    private class TextAdapter extends RecyclerView.Adapter<TextHolder> {

        private StringBuilder mCharacters;
        private volatile int mItemCount;
        private volatile int mLastBoundPosition;

        public TextAdapter() {
            mCharacters = new StringBuilder();
        }

        @Override
        public TextHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.list_item_text, parent, false);
            return new TextHolder(view);
        }

        // Arrange for a reasonable range of additional characters to be in the TextAdapter
        // beyond those that have been bound to TextHolders for display in the RecyclerView.
        // Note that this design works because the RecyclerView limits how far ahead it creates
        // and binds TextHolders. If it were really aggressive and bound everything the TextAdapter
        // had available, then we'd have the makings of an infinite loop -- the process would keep
        // running even without scrolling, generating more and more non-displayed characters.
        // However, it isn't that aggressive. Rather, the scrolling limits the binding (with some
        // margin) and the code here makes the binding in turn limit the production (with more
        // margin), so the rate of scrolling ultimately throttles the rate of the whole process.
        private static final int LOW_WATER = 50;
        private static final int HIGH_WATER = 100;

        private final Lock flowControl = new ReentrantLock();
        private final Condition demand = flowControl.newCondition();

        public void awaitDemand() { // meant to be called from another thread
            try {
                flowControl.lock();
                while (mItemCount - mLastBoundPosition > HIGH_WATER) {
                    demand.await();
                }
            } catch (InterruptedException e) {
                Log.e("PiScroll", "unexpected interruption", e);
            } finally {
                flowControl.unlock();
            }
        }

        @Override
        public void onBindViewHolder(TextHolder holder, int position) {
            mLastBoundPosition = position;
            try {
                flowControl.lock();
                if (mItemCount - mLastBoundPosition < LOW_WATER) {
                    demand.signal();
                }
            } finally {
                flowControl.unlock();
            }
            holder.bindText(mCharacters.subSequence(position, position + 1));
        }

        @Override
        public int getItemCount() { // safe to call from other threads
            return mItemCount;
        }

        public void append(CharSequence s) {
            int oldLength = mCharacters.length();
            mCharacters.append(s);
            mItemCount = mCharacters.length();
            notifyItemRangeInserted(oldLength, s.length());
        }
    }
}
