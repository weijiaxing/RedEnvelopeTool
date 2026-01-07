package com.redpacket.tool;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Random;

/**
 * 红包助手核心服务类
 * 负责监听微信通知和界面变动，并自动执行点击操作
 */
public class RedPacketService extends AccessibilityService {

    private static final String TAG = "RedPacketService";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    
    // 用于防重复点击：记录最近点击的红包节点 HashCode 或时间戳
    private int lastClickedHash = 0;
    private long lastClickedTime = 0;
    private static final long CLICK_INTERVAL = 2000; // 同一红包点击间隔，2秒
    
    // 随机延迟生成器
    private final Random random = new Random();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "红包助手无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 过滤微信包名，确保安全
        if (!WECHAT_PACKAGE.equals(event.getPackageName())) return;

        int eventType = event.getEventType();
        // 仅在日志中打印关键事件
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED || 
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "接收到关键事件: " + AccessibilityEvent.eventTypeToString(eventType));
        }

        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                handleNotification(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleWindowChanged();
                break;
        }
    }

    /**
     * 处理通知栏事件
     */
    private void handleNotification(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        if (texts != null && !texts.isEmpty()) {
            for (CharSequence text : texts) {
                String content = text.toString();
                if (content.contains("[微信红包]")) {
                    Log.d(TAG, "检测到红包消息: " + content);
                    if (event.getParcelableData() != null && event.getParcelableData() instanceof android.app.Notification) {
                        android.app.Notification notification = (android.app.Notification) event.getParcelableData();
                        try {
                            notification.contentIntent.send();
                            Log.d(TAG, "已模拟点击通知栏");
                        } catch (Exception e) {
                            Log.e(TAG, "跳转失败: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 处理微信界面变动
     */
    private void handleWindowChanged() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "无法获取当前活动窗口的根节点");
            return;
        }

        // 打印当前界面的根节点类名，辅助调试
        Log.v(TAG, "当前根节点类名: " + rootNode.getClassName());

        // 优先查找红包弹窗中的“開”按钮
        if (!findAndClickOpenButton(rootNode)) {
            // 如果不是弹窗页面，再查找聊天界面的红包
            findAndClickRedPacket(rootNode);
        }
        
        rootNode.recycle();
    }

    /**
     * 查找并点击“领取红包”
     */
    private void findAndClickRedPacket(AccessibilityNodeInfo node) {
        if (node == null) return;

        // 尝试匹配多种可能的文本
        String[] keywords = {"领取红包", "微信红包"};
        for (String keyword : keywords) {
            List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByText(keyword);
            if (nodes != null && !nodes.isEmpty()) {
                Log.d(TAG, "通过关键词 '" + keyword + "' 找到 " + nodes.size() + " 个红包节点");
                // 获取最后一个红包（通常是最新的）
                AccessibilityNodeInfo lastNode = nodes.get(nodes.size() - 1);
                
                // 防重复点击逻辑
                int currentHash = lastNode.hashCode();
                long currentTime = System.currentTimeMillis();
                
                if (currentHash != lastClickedHash || (currentTime - lastClickedTime > CLICK_INTERVAL)) {
                    lastClickedHash = currentHash;
                    lastClickedTime = currentTime;
                    
                    Log.d(TAG, "发现新红包 [" + keyword + "]，准备点击...");
                    clickNode(lastNode);
                    return; // 找到并处理一个后立即返回
                }
            }
        }
    }

    /**
     * 查找并点击红包弹窗中的 “開” 按钮
     * @return 是否处理了弹窗
     */
    private boolean findAndClickOpenButton(AccessibilityNodeInfo node) {
        // 微信红包弹窗通常包含特殊的节点特征，这里通过遍历查找
        return iterateAndClickOpen(node);
    }

    private boolean iterateAndClickOpen(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence className = node.getClassName();
        // “開”按钮在微信 8.0+ 可能是 Button 或 ImageButton，且通常没有文字
        // 且位于屏幕中心区域
        if (className != null && (className.toString().contains("Button") || className.toString().contains("ImageButton"))) {
            if (node.getText() == null || node.getText().length() == 0) {
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                
                // 常见的“開”按钮位于屏幕水平正中，垂直方向在 50%-75% 之间
                if (rect.centerX() > screenWidth * 0.4 && rect.centerX() < screenWidth * 0.6 &&
                    rect.centerY() > screenHeight * 0.5 && rect.centerY() < screenHeight * 0.8) {
                    
                    Log.d(TAG, "定位到“開”按钮坐标: " + rect.toShortString());
                    performDelayedGesture(rect.centerX(), rect.centerY());
                    return true;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (iterateAndClickOpen(child)) return true;
            }
        }
        return false;
    }

    /**
     * 执行带延迟的手势点击，防止被识别为机器人
     */
    private void performDelayedGesture(final int x, final int y) {
        int delay = 500 + random.nextInt(700); // 500ms - 1200ms
        Log.d(TAG, "随机延迟: " + delay + "ms");
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "模拟点击位置: (" + x + ", " + y + ")");
        }, delay);
    }

    /**
     * 辅助方法：点击节点（如果节点不可点击，则尝试点击其父节点）
     */
    private void clickNode(AccessibilityNodeInfo node) {
        if (node == null) {
            Log.e(TAG, "点击失败：找不到可点击的父节点");
            return;
        }
        if (node.isClickable()) {
            Log.d(TAG, "正在点击节点: " + node.getClassName());
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            clickNode(node.getParent());
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "服务已中断");
    }
}
