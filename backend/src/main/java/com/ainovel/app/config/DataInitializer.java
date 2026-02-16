package com.ainovel.app.config;

import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private WorldRepository worldRepository;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private GlobalSettingsRepository globalSettingsRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${spring.mail.host:}")
    private String defaultSmtpHost;
    @Value("${spring.mail.port:587}")
    private Integer defaultSmtpPort;
    @Value("${spring.mail.username:}")
    private String defaultSmtpUsername;
    @Value("${spring.mail.password:}")
    private String defaultSmtpPassword;

    @Override
    public void run(String... args) {
        User user = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setEmail("admin@example.com");
            u.setPasswordHash(passwordEncoder.encode("password"));
            u.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
            u.setCredits(999999);
            return userRepository.save(u);
        });

        GlobalSettings global = globalSettingsRepository.findTopByOrderByUpdatedAtDesc().orElseGet(GlobalSettings::new);
        if (global.getId() == null) {
            global.setRegistrationEnabled(true);
            global.setMaintenanceMode(false);
            global.setCheckInMinPoints(10);
            global.setCheckInMaxPoints(50);
            if (defaultSmtpHost != null && !defaultSmtpHost.isBlank()) global.setSmtpHost(defaultSmtpHost);
            if (defaultSmtpPort != null) global.setSmtpPort(defaultSmtpPort);
            if (defaultSmtpUsername != null && !defaultSmtpUsername.isBlank()) global.setSmtpUsername(defaultSmtpUsername);
            if (defaultSmtpPassword != null && !defaultSmtpPassword.isBlank()) global.setSmtpPassword(defaultSmtpPassword);
            globalSettingsRepository.save(global);
        }

        if (storyRepository.count() == 0) {
            Story story = new Story();
            story.setUser(user);
            story.setTitle("赛博侦探：霓虹雨");
            story.setSynopsis("在2077年的新东京，一名失去记忆的侦探试图找回过去");
            story.setGenre("科幻");
            story.setTone("阴郁");
            story.setStatus("draft");
            storyRepository.save(story);

            Outline outline = new Outline();
            outline.setStory(story);
            outline.setTitle("主线大纲 V1");
            outline.setContentJson("{\"chapters\":[{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"title\":\"第一章：觉醒\",\"summary\":\"主角在垃圾堆中醒来，发现自己只有一只机械臂。\",\"order\":1,\"scenes\":[{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"title\":\"垃圾场苏醒\",\"summary\":\"雨夜，霓虹灯闪烁，主角睁开眼。\",\"content\":null,\"order\":1},{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"title\":\"遭遇拾荒者\",\"summary\":\"几个拾荒者试图拆解主角。\",\"content\":null,\"order\":2}]}]}");
            outlineRepository.save(outline);

            Story story2 = new Story();
            story2.setUser(user);
            story2.setTitle("龙之谷的最后守护者");
            story2.setSynopsis("当古老的封印破碎，年轻的牧羊人必须拿起剑。");
            story2.setGenre("奇幻");
            story2.setTone("热血");
            story2.setStatus("draft");
            storyRepository.save(story2);
        }

        if (worldRepository.count() == 0) {
            World world = new World();
            world.setUser(user);
            world.setName("新东京 2077");
            world.setTagline("高科技低生活");
            world.setStatus("active");
            world.setVersion("1.0.0");
            world.setModulesJson("{}");
            world.setModuleProgressJson("{}");
            worldRepository.save(world);

            World world2 = new World();
            world2.setUser(user);
            world2.setName("龙脊大陆");
            world2.setTagline("群龙远去，余烬仍温热");
            world2.setStatus("draft");
            world2.setVersion("0.1.0");
            world2.setModulesJson("{}");
            world2.setModuleProgressJson("{}");
            worldRepository.save(world2);
        }

        if (materialRepository.count() == 0) {
            Material material = new Material();
            material.setUser(user);
            material.setTitle("赛博义体型号大全");
            material.setType("text");
            material.setSummary("常见义体型号与特点");
            material.setContent("常见义体型号包含军用与民用两大类...");
            material.setTagsJson("[\"设定\",\"科技\"]");
            material.setStatus("approved");
            materialRepository.save(material);

            Material pending = new Material();
            pending.setUser(user);
            pending.setTitle("龙脊大陆：地理草稿");
            pending.setType("text");
            pending.setSummary("待审核的世界观设定草稿");
            pending.setContent("北境为永冻高原，南境为潮湿雨林，中央是古龙遗骸形成的山脉...");
            pending.setTagsJson("[\"地理\",\"奇幻\"]");
            pending.setStatus("pending");
            materialRepository.save(pending);

            Material material2 = new Material();
            material2.setUser(user);
            material2.setTitle("写作提示：悬疑节奏");
            material2.setType("text");
            material2.setSummary("悬疑章节节奏与线索投放");
            material2.setContent("每章至少一个可验证线索；每三章一次反转；误导线索需可回溯解释...");
            material2.setTagsJson("[\"技巧\",\"悬疑\"]");
            material2.setStatus("approved");
            materialRepository.save(material2);
        }
    }
}
