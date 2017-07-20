package com.ibooking.dao;

import java.util.List;

import com.ibooking.po.*;

public interface MenuTypeDao {
	MenuType get(Integer id);
	
	Integer save(MenuType menuType);
	
	void update(MenuType menuType);
	
	void delete(MenuType menuType);
	
	List<MenuType> findAll();
	
	List<MenuType> findByName(String menuTypeName);
}
