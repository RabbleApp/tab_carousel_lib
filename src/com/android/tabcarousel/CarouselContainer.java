/*
 * Copyright (C) 2013 Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tabcarousel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ObjectAnimator;

import java.lang.ref.WeakReference;

/**
 * This is a horizontally scrolling carousel with 2 tabs.
 */
public class CarouselContainer extends HorizontalScrollView implements OnTouchListener {

    /**
     * Max number of tabs
     */
    private static final int MAX_TABS = 5;
    
    /**
     * Y coordinate of the tab at the given index was selected
     */
    
    private static final float[] Y_COORDINATE = new float[MAX_TABS];

    /**
     * Alpha layer to be set on the lable view
     */
    private static final float MAX_ALPHA = 0.6f;

    /**
     * Tab width as defined as a fraction of the screen width
     */
    private float mTabWidthScreenFraction;

    /**
     * Height of the tab label
     */
    private final int mTabDisplayLabelHeight;

    /**
     * Used to determine is the carousel is animating
     */
    private boolean mTabCarouselIsAnimating;

    /**
     * Indicates that both tabs are to be used if true, false if only one
     */
    private boolean mMultiTabs = true;

    /**
     * Interface invoked when the user interacts with the carousel
     */
    private OnCarouselListener mCarouselListener;
    
    /**
     * Array with all the tabs
     */
    private CarouselTab mTabs[] = new CarouselTab[MAX_TABS];
    
    /**
     * total amount of tabs
     */
    private int mTabCount = 0;

    /**
     * Allowed horizontal scroll length
     */
    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;

    /**
     * Allowed vertical scroll length
     */
    private int mAllowedVerticalScrollLength = Integer.MIN_VALUE;

    /**
     * The last scrolled position
     */
    private int mLastScrollPosition = Integer.MIN_VALUE;

    /**
     * Current tab index
     */
    private int mCurrentTab = 0;

    /**
     * Factor to scale scroll-amount sent to {@code #mCarouselListener}
     */
    private float mScrollScaleFactor = 1.0f;

    /**
     * True to scroll to the pager's current position, false otherwise
     */
    private boolean mScrollToCurrentTab = false;

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view
     */
    public CarouselContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Add the onTouchListener
        setOnTouchListener(this);
        // Retrieve the carousel dimensions
        final Resources res = getResources();
        // Height of the label
        mTabDisplayLabelHeight = res.getDimensionPixelSize(R.dimen.carousel_label_height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        // Compute the width of a tab as a fraction of the screen width
        final int tabWidth = Math.round(mTabWidthScreenFraction * screenWidth);

        // Find the allowed scrolling length by subtracting the current visible
        // screen width
        // from the total length of the tabs.
        mAllowedHorizontalScrollLength = tabWidth * mTabCount - screenWidth;

        // Scrolling by mAllowedHorizontalScrollLength causes listeners to
        // scroll by the entire screen amount; compute the scale-factor
        // necessary to make this so.
        if (mAllowedHorizontalScrollLength == 0) {
            // Guard against divide-by-zero.
            // This hard-coded value prevents a crash, but won't result in the
            // desired scrolling behavior. We rely on the framework calling
            // onMeasure()
            // again with a non-zero screen width.
            mScrollScaleFactor = 1.0f;
        } else {
            mScrollScaleFactor = screenWidth / mAllowedHorizontalScrollLength;
        }

        final int tabHeight = getResources().getDimensionPixelSize(R.dimen.carousel_label_height) + getResources().getDimensionPixelSize(R.dimen.carousel_image_height);
        // Set the child layout's to be mTabCount * the computed tab
        // width so that the layout's children (which are the tabs) will evenly
        // split that width.
        if (getChildCount() > 0) {
            final View child = getChildAt(0);

            // Add 1 dip of separation between the tabs
            final int seperatorPixels = (int) (TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()) + 0.5f);

            if (mMultiTabs) {
                final int size = mTabCount * tabWidth + (mTabCount - 1) * seperatorPixels;
                child.measure(measureExact(size), measureExact(tabHeight));
            } else {
                child.measure(measureExact(screenWidth), measureExact(tabHeight));
            }
        }

        mAllowedVerticalScrollLength = tabHeight - mTabDisplayLabelHeight;
        setMeasuredDimension(resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(tabHeight, heightMeasureSpec));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!mScrollToCurrentTab) {
            return;
        }
        mScrollToCurrentTab = false;
        Utils.doAfterLayout(this, new Runnable() {
            @Override
            public void run() {
                scrollTo(mCurrentTab == 0 ? 0 : mAllowedHorizontalScrollLength, 0);
                updateAlphaLayers();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);

        // Guard against framework issue where onScrollChanged() is called twice
        // for each touch-move event. This wreaked havoc on the tab-carousel:
        // the
        // view-pager moved twice as fast as it should because we called
        // fakeDragBy()
        // twice with the same value.
        if (mLastScrollPosition == x) {
            return;
        }

        // Since we never completely scroll the about/updates tabs off-screen,
        // the draggable range is less than the width of the carousel. Our
        // listeners don't care about this... if we scroll 75% percent of our
        // draggable range, they want to scroll 75% of the entire carousel
        // width, not the same number of pixels that we scrolled.
        final int scaledL = (int) (x * mScrollScaleFactor);
        final int oldScaledL = (int) (oldX * mScrollScaleFactor);
        mCarouselListener.onCarouselScrollChanged(scaledL, y, oldScaledL, oldY);

        mLastScrollPosition = x;
        updateAlphaLayers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final boolean interceptTouch = super.onInterceptTouchEvent(ev);
        if (interceptTouch) {
            mCarouselListener.onTouchDown();
        }
        return interceptTouch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCarouselListener.onTouchDown();
                return true;
            case MotionEvent.ACTION_UP:
                mCarouselListener.onTouchUp();
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * @return True if the carousel is currently animating, false otherwise
     */
    public boolean isTabCarouselIsAnimating() {
        return mTabCarouselIsAnimating;
    }

    /**
     * Reset the carousel to the start position
     */
    public void reset() {
        scrollTo(0, 0);
        setCurrentTab(0);
        moveToYCoordinate(0, 0);
    }
    
    /**
     * Clears all stored y coordinates
     */
    public void clearYCoordinates(){
        for(int i = 0; i < MAX_TABS; i++){
            Y_COORDINATE[i] = 0f;
        }
    }

    /**
     * Store this information as the last requested Y coordinate for the given
     * tabIndex.
     * 
     * @param tabIndex The tab index being stored
     * @param y The Y cooridinate to move to
     */
    public void storeYCoordinate(int tabIndex, float y) {
        Y_COORDINATE[tabIndex] = y;
    }

    /**
     * Restore the Y position of this view to the last manually requested value.
     * This can be done after the parent has been re-laid out again, where this
     * view's position could have been lost if the view laid outside its
     * parent's bounds.
     * 
     * @param duration The duration of the animation
     * @param tabIndex The index to restore
     */
    public void restoreYCoordinate(int duration, int tabIndex) {
        final float storedYCoordinate = getStoredYCoordinateForTab(tabIndex);
        if(Utils.hasHoneycomb()){
            final Interpolator interpolator = AnimationUtils.loadInterpolator(getContext(),
                    android.R.anim.accelerate_decelerate_interpolator);
    
            final ObjectAnimator animator = ObjectAnimator.ofFloat(this, "y", storedYCoordinate);
            animator.addListener(mTabCarouselAnimatorListener);
            animator.setInterpolator(interpolator);
            animator.setDuration(duration);
            animator.start();
        }
    }

    /**
     * Request that the view move to the given Y coordinate. Also store the Y
     * coordinate as the last requested Y coordinate for the given tabIndex.
     * 
     * @param tabIndex The tab index being stored
     * @param y The Y cooridinate to move to
     */
    public void moveToYCoordinate(int tabIndex, float y) {
        storeYCoordinate(tabIndex, y);
        restoreYCoordinate(0, tabIndex);
    }

    /**
     * Used to propely call {@code #onMeasure(int, int)}
     * 
     * @param yesOrNo Yes to indicate both tabs will be used in the carousel,
     *            false to indicate only one
     */
    public void setUsesDualTabs(boolean yesOrNo) {
        mMultiTabs = yesOrNo;
    }

    /**
     * Set the given {@link OnCarouselListener} to handle carousel events
     */
    public void setListener(OnCarouselListener carouselListener) {
        mCarouselListener = carouselListener;
    }

    /**
     * Updates the tab selection
     * 
     * @param position The index to update
     */
    public void setCurrentTab(int position) {
        CarouselTab selected, deselected[] = new CarouselTab[MAX_TABS];
        int j = 0;
        for(int i = 0; i < mTabCount; i++){
            if(i != position){
                deselected[j++] = mTabs[i];
            }
        }
        selected = mTabs[position];
        selected.setSelected(true);
        for(CarouselTab deselectedTab : deselected){
            if(deselectedTab != null) deselectedTab.setSelected(false);
        }
        mCurrentTab = position;
    }
    
    /**
     * Initalizes the amount of tabs specified.
     * 
     * @param amountOfTabs
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public void initializeTabs(int amountOfTabs){
        mTabCount = amountOfTabs;
        mTabWidthScreenFraction = 1f/mTabCount;
        for(int i = 0; i < mTabCount; i++){
            LinearLayout tabContainer = (LinearLayout) findViewById(R.id.carousel_tab_container);
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View tab = inflater.inflate(R.layout.carousel_tab, null);
            tab.setId(i);
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int containerWidth;
            if(Utils.hasHoneycombMr2()){
                Point tempPoint = new Point();
                display.getSize(tempPoint);
                containerWidth = tempPoint.x;
            }else{
                containerWidth = display.getWidth();
            }
            tabContainer.addView(tab, containerWidth/mTabCount, LayoutParams.MATCH_PARENT);
            CarouselTab tablayout = (CarouselTab) findViewById(i);
            mTabs[i] = tablayout;
            mTabs[i].setOverlayOnClickListener(new TabClickListener(this, i));
        }
    }
    
    /**
     * Sets the label for a tab
     * 
     * @param index Which label to write on
     * @param label The string to set as the label
     */
    public void setLabel(int index, String label, boolean isSelected) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                mTabs[i].setLabel(label);
                mTabs[i].setSelected(isSelected);
            }
        }
    }

    /**
     * Sets a drawable as the content of the tab {@link ImageView}
     * 
     * @param index Which {@link ImageView}
     * @param resId The resource identifier of the the drawable
     */
    public void setImageResource(int index, int resId) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                mTabs[i].setImageResource(resId);
            }
        }
    }

    /**
     * Sets a drawable as the content of the tab {@link ImageView}
     * 
     * @param index Which {@link ImageView}
     * @param drawable The {@link Drawable} to set
     */
    public void setImageDrawable(int index, Drawable drawable) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                mTabs[i].setImageDrawable(drawable);
            }
        }
    }

    /**
     * Sets a bitmap as the content of the tab {@link ImageView}
     * 
     * @param index Which {@link ImageView}
     * @param bm The {@link Bitmap} to set
     */
    public void setImageBitmap(int index, Bitmap bm) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                mTabs[i].setImageBitmap(bm);
            }
        }
    }

    /**
     * Used to return the {@link ImageView} from one of the tabs
     * 
     * @param index The index returning the {@link ImageView}
     * @return The {@link ImageView} from one of the tabs
     */
    public ImageView getImage(int index) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                return mTabs[i].getImage();
            }
        }
        throw new IllegalStateException("Invalid tab position " + index);
    }

    /**
     * Used to return the label from one of the tabs
     * 
     * @param index The index returning the label
     * @return The label from one of the tabs
     */
    public TextView getLabel(int index) {
        for(int i = 0; i < mTabCount; i++){
            if(i == index){
                return mTabs[i].getLabel();
            }
        }
        throw new IllegalStateException("Invalid tab position " + index);
    }

    /**
     * Returns the stored Y coordinate of this view the last time the user was
     * on the selected tab given by tabIndex.
     * 
     * @param tabIndex The tab index use to return the Y value
     */
    public float getStoredYCoordinateForTab(int tabIndex) {
        return Y_COORDINATE[tabIndex];
    }

    /**
     * Returns the number of pixels that this view can be scrolled horizontally
     */
    public int getAllowedHorizontalScrollLength() {
        return mAllowedHorizontalScrollLength;
    }

    /**
     * Returns the number of pixels that this view can be scrolled vertically
     * while still allowing the tab labels to still show
     */
    public int getAllowedVerticalScrollLength() {
        return mAllowedVerticalScrollLength;
    }

    /**
     * @param size The size of the measure specification
     * @return The measure specifiction based on {@link MeasureSpec.#EXACTLY}
     */
    private int measureExact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    /**
     * Sets the correct alpha layers over the tabs.
     */
    private void updateAlphaLayers() {
        float alpha = mLastScrollPosition * MAX_ALPHA / mAllowedHorizontalScrollLength;
        alpha = Utils.clamp(alpha, 0.0f, 1.0f);
    }

    /**
     * This listener keeps track of whether the tab carousel animation is
     * currently going on or not, in order to prevent other simultaneous changes
     * to the Y position of the tab carousel which can cause flicker.
     */
    private final AnimatorListener mTabCarouselAnimatorListener = new AnimatorListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAnimationCancel(Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAnimationEnd(Animator animation) {
            mTabCarouselIsAnimating = false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAnimationRepeat(Animator animation) {
            mTabCarouselIsAnimating = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAnimationStart(Animator animation) {
            mTabCarouselIsAnimating = true;
        }
    };

    /** When pressed, selects the corresponding tab */
    private static final class TabClickListener implements OnClickListener {

        /**
         * Reference to {@link CarouselContainer}
         */
        private final WeakReference<CarouselContainer> mReference;

        /**
         * The {@link CarouselTab} being pressed
         */
        private final int mTab;

        /**
         * @param tab The index of the tab pressed
         */
        public TabClickListener(CarouselContainer carouselHeader, int tab) {
            super();
            mReference = new WeakReference<CarouselContainer>(carouselHeader);
            mTab = tab;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(View v) {
            mReference.get().mCarouselListener.onTabSelected(mTab);
        }
    }

}
