package com.lite.flashlabel;

import java.util.Locale;

/**
 * All user-visible text in one place. The language follows the device: Vietnamese when the
 * system language is Vietnamese, English otherwise. Resolved once when the class loads.
 */
final class Strings {
    private Strings() {
    }

    private static final boolean VI = "vi".equals(Locale.getDefault().getLanguage());

    private static String t(String en, String vi) {
        return VI ? vi : en;
    }

    // Main screen
    static final String LABEL_TYPE = t("Label type", "Loại tem nhãn");
    static final String PAPER_BLACK_MARK = t("Black mark", "Tem đánh dấu");
    static final String PAPER_GAP = t("Gap label", "Giấy tệp");
    static final String PAPER_CONTINUOUS = t("Continuous", "Giấy liền liên");
    static final String WIDTH_MM = t("Width (mm)", "Chiều rộng (mm)");
    static final String HEIGHT_MM = t("Height (mm)", "Chiều dài (mm)");
    static final String DIRECTION = t("Print direction", "Hướng tem nhãn");
    static final String DENSITY = t("Print density", "Mật độ in");
    static final String SPEED = t("Print speed", "Tốc độ in");
    static final String DITHER = t("Photo dithering", "In ảnh");
    static final String COMPRESS = t("Compress image data", "Nén dữ liệu ảnh");
    static final String CROP_EDGES = t("Crop edges (mm)", "Cắt lề (mm)");
    static final String CROP_TOP = t("Top", "Trên");
    static final String CROP_LEFT = t("Left", "Trái");
    static final String CROP_RIGHT = t("Right", "Phải");
    static final String CROP_BOTTOM = t("Bottom", "Dưới");
    static final String CROP_NONE = t("None", "Không");
    static final String RESET = t("Reset", "Đặt lại");
    static final String CROP_NO_PREVIEW = t("Open a file to see a preview", "Mở tệp để xem trước");
    static final String CROP_HINT = t("Applied to every page before printing. Shaded areas are removed.",
            "Áp dụng cho mọi trang trước khi in. Vùng tô tối sẽ bị cắt bỏ.");
    static final String PICK_FILE = t("Choose PDF / image", "Chọn PDF / ảnh");
    static final String ADD_FILE = t("Add file", "Thêm tệp");
    static final String PRINT_NOW = t("Print now", "In ngay");
    static final String PRINTED = t("Printed", "Đã in");

    // Printer connection
    static final String NOT_CONNECTED = t("Not connected", "Chưa kết nối");
    static final String CONNECTING = t("Connecting…", "Đang kết nối…");
    static final String CONNECT_FAILED = t("Connection failed", "Kết nối thất bại");
    static final String CHOOSE_PRINTER = t("Choose printer", "Chọn máy in");
    static final String NO_BLUETOOTH = t("This device has no Bluetooth", "Thiết bị không hỗ trợ Bluetooth");
    static final String STILL_CONNECTING = t("Connecting to the printer, please wait…", "Đang kết nối máy in, vui lòng đợi…");

    // Dialogs
    static final String OK = t("OK", "Xác nhận");
    static final String CANCEL = t("Cancel", "Huỷ");
    static final String LOADING = t("Loading…", "Đang tải…");
    static final String DOWNLOADING = t("Downloading…", "Đang tải xuống…");
    static final String OPEN_FAILED = t("Cannot open file: ", "Không mở được tệp: ");
    static final String SHARE_SKIPPED_PRINTING = t("Printing in progress, the shared file was skipped",
            "Đang in, đã bỏ qua tệp được chia sẻ");
    static final String SHARE_TITLE = t("New shared file", "Tệp mới được chia sẻ");
    static final String SHARE_MESSAGE = t("Replace the open files, or add the new file to the list?",
            "Thay thế các tệp đang mở, hay thêm tệp mới vào danh sách?");
    static final String SHARE_REPLACE = t("Replace", "Thay thế");
    static final String SHARE_APPEND = t("Add", "Thêm vào");
    static final String NO_LINK = t("No PDF or image link found in the shared text", "Không tìm thấy liên kết PDF hoặc ảnh trong nội dung chia sẻ");
    static final String NO_FILE = t("Choose a PDF or image to print", "Hãy chọn tệp PDF hoặc ảnh để in");
    static final String NO_PAGE_SELECTED = t("No page selected to print", "Chưa chọn trang nào để in");
    static final String REPRINT_TITLE = t("Print again?", "In lại?");
    static final String REPRINT_MESSAGE = t("This document has already been printed. Print it again?",
            "Tài liệu này đã được in. Bạn có muốn in lại không?");

    // Print progress / printer status
    static final String APP_NAME = "FlashLabel Lite";
    static final String PRINT_DONE = t("Printing finished", "Đã in xong");
    static final String PRINT_CANCELLED = t("Printing cancelled", "Đã huỷ in");
    static final String PRINTING = t("Printing", "Đang in");
    static final String SENDING_PAGE = t("Sending page ", "Đang truyền trang ");
    static final String WAITING_FINISH = t("Waiting for the printer to finish…", "Đang chờ máy in in xong…");
    static final String PRINTER_READY = t("Printer ready", "Máy in sẵn sàng");
    static final String PRINTER_BUSY = t("Printer is busy…", "Máy in đang in…");
    static final String COVER_OPEN = t("Printer cover is open", "Máy in đang mở nắp");
    static final String NO_PAPER = t("Printer is out of paper", "Máy in hết giấy");
    static final String COVER_OR_PAPER = t("Please check whether the printer cover is open or it is out of paper",
            "Vui lòng kiểm tra xem máy in có bị mở nắp hoặc thiếu giấy");
    static final String LOW_BATTERY = t("Printer battery is low", "Nguồn điện máy in thấp");
    static final String WAITING_REPLY = t("Waiting for the printer to respond", "Đang chờ máy in phản hồi");
    static final String DEVICE_ERROR = t("Device error", "Lỗi thiết bị");
    static final String CONNECTION_LOST = t("Printer connection lost", "Mất kết nối máy in");
    static final String FINISH_UNCONFIRMED = t("All data was sent, but the printer did not confirm it finished",
            "Đã gửi xong dữ liệu nhưng không xác nhận được máy in đã in xong");
}
