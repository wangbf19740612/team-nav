package com.tuituidan.openhub.service;

import com.tuituidan.openhub.bean.dto.CardIconDto;
import com.tuituidan.openhub.bean.entity.Card;
import com.tuituidan.openhub.consts.CardTypeEnum;
import com.tuituidan.openhub.consts.Consts;
import com.tuituidan.openhub.consts.UploadTypeEnum;
import com.tuituidan.openhub.exception.ResourceWriteException;
import com.tuituidan.openhub.repository.CardRepository;
import com.tuituidan.openhub.util.FileExtUtils;
import com.tuituidan.openhub.util.HttpUtils;
import com.tuituidan.openhub.util.QrCodeUtils;
import com.tuituidan.openhub.util.ResponseUtils;
import com.tuituidan.openhub.util.StringExtUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

/**
 * CommonService.
 *
 * @author tuituidan
 * @version 1.0
 * @date 2021/3/5
 */
@Service
@Slf4j
public class CommonService implements ApplicationRunner {

    private static final List<String> CARD_ICONS = new ArrayList<>();

    private static final String CARD_ICON_PATH = "/ext-resources/images/default/";

    @Resource
    private CardRepository cardRepository;

    @Resource
    private AttachmentService attachmentService;

    /**
     * 初始化
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadCardIcons();
    }

    /**
     * 加载放置在/ext-resources/images/default路径下的图片图标用于卡片图标选择
     */
    public void loadCardIcons() {
        CARD_ICONS.clear();
        File root = new File(Consts.ROOT_DIR + CARD_ICON_PATH);
        if (!root.exists()) {
            return;
        }
        File[] files = root.listFiles();
        if (files == null || files.length <= 0) {
            return;
        }
        for (File file : files) {
            CARD_ICONS.add(file.getName());
        }
    }

    /**
     * 文件上传，包括原型和图标的
     *
     * @param file 文件
     * @param type 文件类型 images 或者 modules
     * @return 保存路径
     */
    public String upload(MultipartFile file, String type) {
        String savePath = formatSavePath(type, file);
        File saveFile = new File(Consts.ROOT_DIR + savePath);
        try {
            FileUtils.forceMkdirParent(saveFile);
        } catch (IOException ex) {
            throw new ResourceWriteException("父目录生成失败", ex);
        }
        try (InputStream in = file.getInputStream();
                OutputStream out = new FileOutputStream(saveFile)) {
            IOUtils.copy(in, out);
        } catch (Exception ex) {
            throw new ResourceWriteException("文件写入失败", ex);
        }
        if (UploadTypeEnum.DEFAULT.getType().equals(type)) {
            CARD_ICONS.add(file.getOriginalFilename());
        }
        if (UploadTypeEnum.ATTACHMENTS.getType().equals(type)) {
            return attachmentService.saveAttachment(savePath, file);
        }
        return savePath;
    }

    private String formatSavePath(String type, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (UploadTypeEnum.DEFAULT.getType().equals(type)) {
            String path = CARD_ICON_PATH + fileName;
            Assert.isTrue(!new File(Consts.ROOT_DIR + path).exists(), "文件名已经存在");
            return path;
        }
        return StringExtUtils.format("/ext-resources/{}/{}/{}.{}",
                type,
                DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()),
                StringExtUtils.getUuid(),
                FilenameUtils.getExtension(fileName));
    }

    /**
     * 修改icon文件名
     *
     * @param fileName fileName
     * @param newName newName
     */
    public void updateIconName(String fileName, String newName) {
        String root = Consts.ROOT_DIR + CARD_ICON_PATH;
        File oldFile = new File(root + fileName);
        String newFileName = newName + "." + FilenameUtils.getExtension(fileName);
        File newFile = new File(root + newFileName);
        Assert.isTrue(oldFile.exists(), "原图标已不存在");
        Assert.isTrue(!newFile.exists(), "无法修改为图标名【" + newName + "】，该图标名已存在");
        Assert.isTrue(oldFile.renameTo(newFile), "文件名修改失败");
        CARD_ICONS.set(CARD_ICONS.indexOf(fileName), newFileName);
        resetRefIcon(fileName, newFileName);
    }

    private void resetRefIcon(String fileName, String newFileName) {
        List<Card> updateList = cardRepository.findAll().stream()
                .filter(item -> Objects.nonNull(item.getIcon())
                        && StringUtils.isNotBlank(item.getIcon().getSrc())
                        && StringUtils.contains(item.getIcon().getSrc(), CardTypeEnum.DEFAULT.getType())
                        && StringUtils.endsWith(item.getIcon().getSrc(), fileName)
                ).map(card -> {
                    CardIconDto icon = card.getIcon();
                    icon.setSrc(icon.getSrc().replace(fileName, newFileName));
                    return card;
                }).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(updateList)) {
            cardRepository.saveAll(updateList);
        }
    }

    /**
     * 图标删除
     *
     * @param fileName fileName
     * @throws IOException IOException
     */
    public void deleteDefaultIcon(String fileName) throws IOException {
        File file = new File(Consts.ROOT_DIR + CARD_ICON_PATH + fileName);
        if (file.exists()) {
            checkIconRef(fileName);
            FileUtils.forceDelete(file);
            CARD_ICONS.removeIf(name -> StringUtils.equals(fileName, name));
        }
    }

    private void checkIconRef(String fileName) {
        List<String> list = cardRepository.findAll().stream()
                .filter(item -> Objects.nonNull(item.getIcon())
                        && StringUtils.isNotBlank(item.getIcon().getSrc())
                        && StringUtils.contains(item.getIcon().getSrc(), CardTypeEnum.DEFAULT.getType())
                        && StringUtils.endsWith(item.getIcon().getSrc(), fileName)
                ).map(Card::getTitle).collect(Collectors.toList());
        Assert.isTrue(CollectionUtils.isEmpty(list), "图标已被卡片【"
                + StringUtils.join(list, ",") + "】使用，不能删除");
    }

    /**
     * 获取卡片的图标
     *
     * @return List
     */
    public List<String> cardIcons() {
        return CARD_ICONS;
    }

    /**
     * 获取卡片对应链接的 favicon.ico 用于icon
     *
     * @param url url
     * @return List
     */
    public List<String> cardIcons(String url) {
        String domainUrl = getDomainUrl(url);
        String docUrl = getFromDocument(domainUrl);
        // 有时候从dom树的link icon中获取的和favicon图标样式并不一样，都返回给用户去选择
        // 但很多时候两个又是一样的，懒得处理了，所以一个地址获取到两个一样的图标的时候是正常的
        List<String> result = new ArrayList<>();
        if (StringUtils.isNotBlank(docUrl)) {
            result.add(docUrl);
        }
        String favUrl = requestFavicon(domainUrl + "/favicon.ico");
        if (StringUtils.isNotBlank(favUrl)) {
            result.add(favUrl);
        }
        return result;
    }

    /**
     * 获取url的根路径，如http://www.test.com/aa/xx.html -> http://www.test.com
     *
     * @param orgUrl 原始url
     * @return url
     */
    private String getDomainUrl(String orgUrl) {
        try {
            URL url = new URL(orgUrl);
            StringBuilder sb = new StringBuilder(url.getProtocol())
                    .append("://").append(url.getHost());
            if (url.getPort() != -1) {
                sb.append(":").append(url.getPort());
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("url解析错误", ex);
        }
    }

    private String getFromDocument(String domainUrl) {
        Document doc = HttpUtils.getJsoupDoc(domainUrl);
        if (Objects.isNull(doc)) {
            return StringUtils.EMPTY;
        }
        Elements links = doc.head().children().select("link[rel~=icon]");
        if (links.isEmpty()) {
            return "";
        }
        String href = links.get(0).attr("href");
        return requestFavicon(formatLinkIcon(domainUrl, href));
    }

    /**
     * 从link拿到的格式很多种，这里统一格式化一下
     *
     * @param domainUrl domainUrl
     * @param href href
     * @return String
     */
    private String formatLinkIcon(String domainUrl, String href) {
        if (StringUtils.startsWith(href, "http")) {
            return href;
        }
        if (StringUtils.startsWith(href, "//")) {
            return StringUtils.substringBefore(domainUrl, "//") + href;
        }
        if (StringUtils.startsWith(href, "/")) {
            return domainUrl + href;
        }
        return domainUrl + "/" + href;
    }

    private String requestFavicon(String url) {
        byte[] body = HttpUtils.toByteArray(url);
        // 要能实际获取到favicon的数据，如果返回是一个html文件，往往是鉴权导致重定向了
        if (body != null && !FileExtUtils.isHtml(body)) {
            return url;
        }
        return "";
    }

    /**
     * generateQrCode
     *
     * @param url url
     */
    public void generateQrCode(String url) {
        HttpServletResponse response = ResponseUtils.getHttpResponse();
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try (OutputStream outputStream = response.getOutputStream()) {
            BufferedImage image = QrCodeUtils.generate(url, 200);
            ImageIO.write(image, "png", outputStream);
        } catch (Exception ex) {
            throw new ResourceWriteException("二维码写入失败");
        }
    }

}
