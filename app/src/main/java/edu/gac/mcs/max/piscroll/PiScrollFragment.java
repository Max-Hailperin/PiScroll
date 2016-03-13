package edu.gac.mcs.max.piscroll;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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

/**
 * Created by Max Hailperin max@gustavus.edu on 3/12/16.
 * Known limitation: when the app is left with the back button and restarted, it starts over.
 */
public class PiScrollFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private SpigotAdapter mAdapter;
    private boolean mIsAutoScrolling;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
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
            // As a standard UI feature, touching the RecyclerView stops any scroll that is in
            // progress (from a fling gesture, for example). Therefore, it seems to also make
            // sense to turn auto scrolling off (if it was on).
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                autoScrollOff();
                return false;
            }
        });

        if (mAdapter == null) {
            mAdapter = new SpigotAdapter(new PiSpigot());
        }
        mRecyclerView.setAdapter(mAdapter);

        mRecyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                autoScrollIfEnabled();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                // do nothing
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

        private TextView mTextView;

        public TextHolder(View itemView) {
            super(itemView);
            mTextView = (TextView) itemView;
        }

        public void bindText(CharSequence text) {
            mTextView.setText(text);
        }
    }

    private class SpigotAdapter extends RecyclerView.Adapter<TextHolder> {

        private static final int INVENTORY_TARGET = 50; // characters generated ahead of binding
        private StringBuilder mCharacters;
        private int mLastBoundPosition;
        private boolean mIsSpigotEnabled;
        private final Handler mHandler;
        private Runnable mRunSpigot;

        public SpigotAdapter(final Spigot s) {
            mCharacters = new StringBuilder();
            s.setWriter(new Writer() {
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
                    mAdapter.append(new String(buf, offset, count));
                    autoScrollIfEnabled();
                }
            });
            mHandler = new Handler();
            mRunSpigot = new Runnable() {
                @Override
                public void run() {
                    if (mIsSpigotEnabled) {
                        s.run();
                        mHandler.post(this);
                    }
                }
            };
            updateSpigotEnablement();
        }

        @Override
        public TextHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.list_item_text, parent, false);
            return new TextHolder(view);
        }

        @Override
        public void onBindViewHolder(TextHolder holder, int position) {
            mLastBoundPosition = position;
            holder.bindText(mCharacters.subSequence(position, position + 1));
            updateSpigotEnablement();
        }

        private void updateSpigotEnablement() {
            boolean old = mIsSpigotEnabled;
            mIsSpigotEnabled = getItemCount() - mLastBoundPosition < INVENTORY_TARGET;
            if (mIsSpigotEnabled && !old) {
                mHandler.post(mRunSpigot);
            }
        }

        @Override
        public int getItemCount() { // safe to call from other threads
            return mCharacters.length();
        }

        private void append(CharSequence s) {
            int oldItemCount = getItemCount();
            mCharacters.append(s);
            notifyItemRangeInserted(oldItemCount, getItemCount() - oldItemCount);
            updateSpigotEnablement();
        }
    }
}
