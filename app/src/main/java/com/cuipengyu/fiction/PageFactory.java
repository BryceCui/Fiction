package com.cuipengyu.fiction;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Vector;

/**
 * Create by    ： 崔鹏宇
 * Time  is     ： 2018/4/20
 * Email        ： cuipengyusoul@gmail.com
 * Github       ： https://github.com/SolitarySoul
 * Instructions ： 工具类
 */
public class PageFactory {
    /**
     * 屏幕的宽和高
     */
    private int mViewHeight, mViewWidth;
    /**
     * 文字绘制区域宽和高
     */
    private int mTextViewVisibleHeight, mTextViewVisibleWidth;
    /**
     * 文字绘制区域对于屏幕的margin
     */
    private int mMarginHeight, mMarginWidth;
    /**
     * 内容绘制
     */
    private Rect rectF;
    /**
     * 文字和标题字体的大小
     */
    private int mTextViewFontSize, mTitleFontSize;
    /**
     * 每页总行数
     */
    private int mPageLineCount;
    /**
     * 行间距
     */
    private int mLineSpace;
    /**
     * 字节长度
     */
    private int mBufferByteLen;
    /**
     * 高效文件内存映射
     */
    private MappedByteBuffer mFileMapByteBuffer;
    /**
     * 当前页和临时页面的位置
     */
    private int curEndPos = 0, curBeginPos = 0, tempEndPos, tempBeginPos;
    /**
     * 当前章节和临时章节
     */
    private int curChapter, tempChapter;
    /**
     * mLines：行数
     * Vector：可实现自动增长的对象数组
     * mLines：存储章节页面的内容
     */
    private Vector<String> mLines = new Vector<>();
    /**
     * 画笔
     * 标题画笔
     * 背景图片
     */
    private Paint mPaint;
    private Paint mTitlePaint;
    private Bitmap mBookPageBg;
    /**
     * 书籍id
     */
    private String mBookId;
    /**
     * 章节的总数
     */
    private int mChapterSize = 0;
    /**
     * 当前页
     */
    private int mCurrentPage = 1;
    /**
     * 字符编码
     */
    private String mCharset = "UTF-8";

    private List<ChapterLink.MixTocBean.ChaptersBean> mChaptersBeanList;
    private OnReadStateChangeListener mChangeListener;

    public PageFactory(String bookId, List<ChapterLink.MixTocBean.ChaptersBean> chaptersBeanList) {
        this(AppScreenUtil.getAppWidth(), AppScreenUtil.getAppHeight(), 15, bookId, chaptersBeanList);
    }

    public PageFactory(int Width, int Height, int FontSize, String bookId, List<ChapterLink.MixTocBean.ChaptersBean> chapters) {
        mViewWidth = Width;
        mViewHeight = Height;
        mTextViewFontSize = FontSize;
        mBookId = bookId;
        //行间距等于字体大小的2/5
        mLineSpace = mTextViewFontSize / 5 * 2;
        mTitleFontSize = AppScreenUtil.dpToPx(16);
        mMarginHeight = AppScreenUtil.dpToPx(15);
        mMarginWidth = AppScreenUtil.dpToPx(15);
        //文字区域的高 = 2倍的边距 -2倍的标题大小-2倍的行间距
        mTextViewVisibleHeight = mViewHeight - mMarginHeight * 2 - mTitleFontSize * 2 - mLineSpace * 2;
        mTextViewVisibleWidth = mViewWidth - mMarginWidth * 2;
        //页面总行数= 屏幕高/内容字体大小+行距
        mPageLineCount = mViewHeight / (mTextViewFontSize + mLineSpace);
        rectF = new Rect(0, 0, mViewWidth, mViewHeight);
        //设置 ANTI_ALIAS_FLAG 属性可以产生平滑的边缘
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTextSize(mTextViewFontSize);
        mPaint.setColor(Color.BLACK);
        //标题画笔设置
        mTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTitlePaint.setTextSize(mTitleFontSize);
        mTitlePaint.setColor(Color.BLACK);
        mBookId = bookId;
        mChaptersBeanList = chapters;
    }

    //从开始第一章位置打开书籍
    public void openBook() {
        openBook(new int[]{0, 0});
    }

    public void openBook(int[] pos) {
        openBook(1, pos);
    }

    //打开具体章节
    public int openBook(int chapter, int[] pos) {
        this.curChapter = chapter;
        this.mChapterSize = mChaptersBeanList.size();
        //如果当前章节大于章节总数 那就相等
        if (curChapter > mChapterSize) curChapter = mChapterSize;
        //获取文件路径
        String path = getBookFile(curChapter).getPath();
        try {
            //创建文件
            File file = new File(path);
            //获取文件长度
            long length = file.length();
            //如果文件长度大于10
            if (length > 10) {
                mBufferByteLen = (int) length;
                /**
                 * 只有RandomAccessFile获取的Channel才能开启任意的这三种模式
                 * FileChannel.MapMode.READ_ONLY：得到的镜像只能读不能写
                 * FileChannel.MapMode.READ_WRITE：得到的镜像可读可写（既然可写了必然可读），对其写会直接更改到存储节点
                 * FileChannel.MapMode.PRIVATE：得到一个私有的镜像，其实就是一个(position, size)区域的副本罢了，也是可读可写，只不过写不会影响到存储节点，就是一个普通的ByteBuffer了
                 * long position(); // 获取当前操作到节点文件的哪个位置
                 */
                mFileMapByteBuffer = new RandomAccessFile(file, "r").getChannel().map(FileChannel.MapMode.READ_ONLY, 0, length);
                curBeginPos = pos[0];
                curEndPos = pos[1];
                //章节监听
                onChapterChanged(curChapter);
                mLines.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 获取章节文件
     *
     * @param chapter
     * @return
     */
    public File getBookFile(int chapter) {
        File file = FileUtils.getChapterFile(mBookId, chapter);
        //获取字节编码
        mCharset = FileUtils.getCharset(file.getAbsolutePath());
        return file;
    }

    private void onChapterChanged(int chapter) {
        if (mChangeListener != null) mChangeListener.onChapterChanged(chapter);
    }

    /**
     * 同步绘制内容
     *
     * @param canvas
     */
    public synchronized void onDraw(Canvas canvas) {
        //如果章节的内容为0 读取下一页
        if (mLines.size() == 0) {
            curEndPos = curBeginPos;
            mLines = pageDown();
        }
    }

    /**
     * 根据指针位置读取下一页内容
     *
     * @return
     */
    private Vector<String> pageDown() {
        //Paragraph 段落
        String strParagraph = "";
        Vector<String> lines = new Vector<>();
        int paraSpace = 0;
        //总行数=文字总可见区域/字体大小和行间距
        mPageLineCount = mTextViewVisibleHeight / (mTextViewFontSize + mLineSpace);
        //当当前段落大小小于 总行数并且 最后位置小于字节长度
        while ((lines.size() < mPageLineCount) && (curEndPos < mBufferByteLen)) {
            //读取下一段落
            byte[] parabuffer = readParagraphForward(curEndPos);
            //更新最后位置
            curEndPos += parabuffer.length;
            try {
                //存储段落和编码
                strParagraph = new String(parabuffer, mCharset);
                // 段落中的换行符去掉，绘制的时候再换行
                strParagraph = strParagraph.replaceAll("\r\n", "  ")
                        .replaceAll("\n", " ");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 读取下一段落
     *
     * @param curEndPos 当前页结束位置指针
     * @return
     */
    private byte[] readParagraphForward(int curEndPos) {
        byte b0;
        int i = curEndPos;
        while (i < mBufferByteLen) {
            b0 = mFileMapByteBuffer.get(i++);
            if (b0 == 0x0a) {
                break;
            }
        }
        int nParaSize = i - curEndPos;
        byte[] buf = new byte[nParaSize];
        for (i = 0; i < nParaSize; i++) {
            buf[i] = mFileMapByteBuffer.get(curEndPos + i);
        }
        return buf;
    }

}
