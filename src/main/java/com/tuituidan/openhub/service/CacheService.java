package com.tuituidan.openhub.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tuituidan.openhub.bean.entity.Category;
import com.tuituidan.openhub.bean.entity.Role;
import com.tuituidan.openhub.bean.entity.RoleCategory;
import com.tuituidan.openhub.bean.entity.UserStar;
import com.tuituidan.openhub.repository.CategoryRepository;
import com.tuituidan.openhub.repository.RoleCategoryRepository;
import com.tuituidan.openhub.repository.RoleRepository;
import com.tuituidan.openhub.repository.UserStarRepository;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * CacheService.
 *
 * @author tuituidan
 * @version 1.0
 * @date 2024/2/14
 */
@Service
public class CacheService implements ApplicationRunner {

    @Getter
    private final Cache<String, Category> categoryCache = Caffeine.newBuilder().build();

    @Getter
    private final Cache<String, Role> roleCache = Caffeine.newBuilder().build();

    @Getter
    private final Cache<String, List<Role>> categoryRolesCache = Caffeine.newBuilder().build();

    @Getter
    private final Cache<String, List<String>> userStarCardIdsCache = Caffeine.newBuilder().build();

    @Resource
    private CategoryRepository categoryRepository;

    @Resource
    private RoleRepository roleRepository;

    @Resource
    private RoleCategoryRepository roleCategoryRepository;

    @Resource
    private UserStarRepository userStarRepository;

    /**
     * getCategory
     *
     * @param id id
     * @return Category
     */
    public Category getCategory(String id) {
        return categoryCache.get(id, key -> categoryRepository.findById(key).orElseThrow(NullPointerException::new));
    }

    /**
     * getRole
     *
     * @param id id
     * @return Role
     */
    public Role getRole(String id) {
        return roleCache.get(id, key -> roleRepository.findById(key).orElseThrow(NullPointerException::new));
    }

    /**
     * getStarCardIds
     *
     * @param userId userId
     * @return List
     */
    public List<String> getStarCardIds(String userId) {
        return userStarCardIdsCache.get(userId, key ->
                userStarRepository.findByUserId(key).stream()
                        .map(UserStar::getCardId).collect(Collectors.toList())
        );
    }

    /**
     * getRolesByCategoryId
     *
     * @param categoryId categoryId
     * @return List
     */
    public List<Role> getRolesByCategoryId(String categoryId) {
        return categoryRolesCache.get(categoryId, id ->
                roleCategoryRepository.findByCategoryId(id).stream()
                        .map(item -> this.getRole(item.getRoleId()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadCategoryCache();
        loadRoleCache();
        loadCategoryRolesCache();
        loadUserStarCardIdsCache();
    }

    private void loadCategoryCache() {
        List<Category> categories = categoryRepository.findAll();
        categoryCache.invalidateAll();
        categories.forEach(category -> categoryCache.put(category.getId(), category));
    }

    private void loadRoleCache() {
        List<Role> roles = roleRepository.findAll();
        roleCache.invalidateAll();
        roles.forEach(item -> roleCache.put(item.getId(), item));
    }

    private void loadCategoryRolesCache() {
        List<RoleCategory> roleCategories = roleCategoryRepository.findAll();
        Map<String, List<String>> categoryRoleIdsMap = roleCategories.stream()
                .collect(Collectors.groupingBy(RoleCategory::getCategoryId,
                        Collectors.mapping(RoleCategory::getRoleId, Collectors.toList())));
        categoryRolesCache.invalidateAll();
        for (Entry<String, List<String>> entry : categoryRoleIdsMap.entrySet()) {
            categoryRolesCache.put(entry.getKey(), entry.getValue().stream()
                    .map(this::getRole).collect(Collectors.toList()));
        }
    }

    private void loadUserStarCardIdsCache() {
        List<UserStar> userStars = userStarRepository.findAll();
        Map<String, List<String>> idsMap = userStars.stream().collect(Collectors.groupingBy(UserStar::getUserId,
                Collectors.mapping(UserStar::getCardId, Collectors.toList())));
        userStarCardIdsCache.invalidateAll();
        if (MapUtils.isNotEmpty(idsMap)) {
            userStarCardIdsCache.putAll(idsMap);
        }
    }

}
