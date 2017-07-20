package com.ibooking.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.struts2.ServletActionContext;

import com.ibooking.dao.*;
import com.ibooking.po.*;
import com.ibooking.service.*;
import com.ibooking.util.WebConstant;
import com.ibooking.vo.*;
import com.ibooking.vo.manager.*;

public class DaoServiceImpl implements DaoService {
	private MenuDao menuDaoHbm;
	private MenuTypeDao menuTypeDaoHbm;
	private UserDao userDaoHbm;
	private OptionDao optionDaoHbm;
	private ShoppingDao shoppingDaoHbm;
	private OrderDao orderDaoHbm;
	private OrderDetailDao orderDetailDaoHbm;
	
	private MenuDao menuDaoRds;
	private MenuTypeDao menuTypeDaoRds;
	private OptionDao optionDaoRds;
	
	private TransDao transDaoRds;
	
	enum OPT {INVALID, UPDATE_MENU_LST, 
		SAVE_MENU, UPDATE_MENU, DELETE_MENU, 
		SAVE_MENU_TYPE, UPDATE_MENU_TYPE, DELETE_MENU_TYPE, 
		SAVE_OPTION, UPDATE_OPTION, DELETE_OPTION};
	private LinkedBlockingQueue<OptInfo> q;
	private Thread thd;

	private static final int defaultMaxPagination = 7;
	
	private static final String defaultPicSavePath = "res/pic/"; 
	private static final String defaultPicDelPath = "res/tmp/";
	
	@Override
	public boolean validatePasswd(String userName, String userPasswd) {
		User user = getUserByName(userName);
		if (user != null && user.getPasswd().endsWith(userPasswd)) {
			return true;
		}else {
			return false;
		}
	}
	
	@Override
	public String getUserAuthByName(String userName) {
		User user = getUserByName(userName);
		if (user != null) {
			return user.getAuth();
		}else {
			return null;
		}
	}
	
	private User getUserByName(String userName) {
		List<User> lstUser = null;
		
		//get the user from hibernate
		lstUser = userDaoHbm.findByName(userName);
		if (lstUser == null || lstUser.size() == 0) {
			return null;
		}
		
		return lstUser.get(0);
	}

	@Override
	public boolean insertUser(String userName, 					
							String userPasswd, 
							String userAuth, 
							String userTel, 
							String userAddr) {
		User user = getUserByName(userName);
		if (user != null) {
			return false;
		}
		
		user = new User();
		user.setName(userName);
		user.setPasswd(userPasswd);
		user.setAuth(userAuth);
		user.setTel(userTel);
		user.setAddr(userAddr);
		user.setId(0);
		//save the user into hibernate
		userDaoHbm.save(user);
		
		return true;
	}

	@Override
	public String getTitle() {
		List<Option> lstOption = null;
		String optionName = "title";
		
		//get the title from redis
		lstOption = optionDaoRds.findByName(optionName);
		
		return lstOption.get(0).getValue();
	}
	
	@Override
	public IndexPageBean getIndexPageBean(int iCurrPage, String userName) {
		List<Menu> lstMenu = null;
		List<MenuType> lstMenuType = null;
		List<Option> lstOption = null;

		MenuTypeBean menuTypeBean;
		ArrayList<MenuTypeBean> lstMenuTypeBean = new ArrayList<MenuTypeBean>();
		IndexPageBean clsIndexPageBean = new IndexPageBean();

		String optionName = "idx_menu_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		boolean bIsNewMenuType = false;
		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		int iMaxMenuOneLine = 4;
		int iMenuNum = 0;
		
		//get the menu from redis
		lstMenu = menuDaoRds.findAll();
		
		//get the menutype from redis
		lstMenuType = menuTypeDaoRds.findAll();
		
		//get the idx_menu_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Menu and MenuType
		if (lstMenu != null && lstMenu.size() != 0 &&
			lstMenuType != null && lstMenuType.size() != 0) {
			for (MenuType menuType : lstMenuType) {
				menuTypeBean = null;
				iMenuNum = 0;
				bIsNewMenuType = false;
				
				for (Menu menu: lstMenu) {
					if (menuType.getName().equals(menu.getType().getName())) {
						iMenuNum++;

						if (!bIsNewMenuType) {
							iLineNum++;
							bIsNewMenuType = true;
						}
						if (iMenuNum > iMaxMenuOneLine) {
							iMenuNum = 0;
							iLineNum++;
						}
						if (iLineNum > iMaxLineOnePage) {
							iLineNum = 1;
							iPageNum++;
						}

						if (iPageNum == iCurrPage) {
							if (menuTypeBean == null) {
								menuTypeBean = new MenuTypeBean();
	
								ArrayList<MenuBean> lst = new ArrayList<MenuBean>();
								menuTypeBean.setLst(lst);
								menuTypeBean.setName(menuType.getName());
							}
	
							MenuBean menuBean = new MenuBean();
							menuBean.setName(menu.getName());
							menuBean.setPrice(String.valueOf(menu.getPrice()));
							menuBean.setAddr(menu.getPicture());
							menuBean.setAmount("");
							//if user had logined, and the amount into this menu
							if (userName != null) {
								Shopping shopping = getShoppingByName(userName, menu.getName());
								if (shopping != null) {
									int amount = shopping.getAmount();
									if (amount > 0) {
										menuBean.setAmount(String.valueOf(amount));
									}
								}
							}
							
							menuTypeBean.getLst().add(menuBean);
						}
					}
				}
				
				if (menuTypeBean != null) {
					lstMenuTypeBean.add(menuTypeBean);
				}
			}
			
			//calc the startpage and endpage
			if (iPageNum <= defaultMaxPagination) {
				iStartPage = 1;
				iEndPage = iPageNum;
			}else {
				if (iCurrPage > defaultMaxPagination/2) {
					iStartPage = iCurrPage - defaultMaxPagination/2;
				}else {
					iStartPage = 1;
				}

				if (iPageNum >= (iCurrPage + defaultMaxPagination/2)) {
					iEndPage = iCurrPage + defaultMaxPagination/2;
				}else {
					iEndPage = iPageNum;
				}
			}
			clsIndexPageBean.setStartPage(iStartPage);
			clsIndexPageBean.setEndPage(iEndPage);
			clsIndexPageBean.setMaxPage(iPageNum);

			clsIndexPageBean.setLst(lstMenuTypeBean);
			
			return clsIndexPageBean;
		}
		
		return null;
	}
	
	@Override
	public int changeShoppingAmount(String userName, 
								String menuName, 
								String menuPrice,
								boolean isInc) {
		int amount = 0;
		
		if (userName == null || menuName == null || 
			userName.length() == 0 || menuName.length() == 0) {
			System.out.println("DaoServiceImpl.changeShoppingAmount() input param is null");
			return WebConstant.INVALID_VALUE;
		}
		
		Shopping shopping = getShoppingByName(userName, menuName);
		if (shopping != null) {
			//increase the number of Shopping already exist
			amount = shopping.getAmount();
			if (isInc) {
				amount++;
			}else {
				if(amount > 0) {
					amount--;
				}
			}
			shopping.setAmount(amount);
			//save the shopping into hibernate
			shoppingDaoHbm.update(shopping);
		}else {
			//create a new Shopping and insert it
			shopping = new Shopping();
			
			shopping.setId(0);
			shopping.setUserName(userName);
			shopping.setMenuName(menuName);
			shopping.setMenuPrice(Integer.valueOf(menuPrice));
			amount = 1;
			shopping.setAmount(amount);
			shopping.setRemark("");
			//save the shopping into hibernate
			shoppingDaoHbm.save(shopping);
		}
		
		return amount;
	}
	
	private Shopping getShoppingByName(String userName, String menuName) {
		List<Shopping> lstShopping = null;
		
		//get the shopping from hibernate
		lstShopping = shoppingDaoHbm.findByName(userName, menuName);
		if (lstShopping == null || lstShopping.size() == 0) {
			return null;
		}
		
		return lstShopping.get(0);
	}
	
	@Override
	public void deleteShopping(String userName, 
								String menuName) {
		if (userName == null || menuName == null || 
			userName.length() == 0 || menuName.length() == 0) {
			System.out.println("DaoServiceImpl.deleteShopping() input param is null");
			return;
		}
		
		Shopping shopping = getShoppingByName(userName, menuName);
		if (shopping != null) {
			//delete the shopping into hibernate
			shoppingDaoHbm.delete(shopping);
		}
		
		return;
	}

	@Override
	public void changeShoppingRemark(String userName, 
								String menuName, 
								String remark) {
		if (userName == null || menuName == null || 
			userName.length() == 0 || menuName.length() == 0) {
			System.out.println("DaoServiceImpl.changeShoppingRemark() input param is null");
			return;
		}
		
		Shopping shopping = getShoppingByName(userName, menuName);
		if (shopping != null) {
			shopping.setRemark(remark);
			//update the shopping into hibernate
			shoppingDaoHbm.update(shopping);
		}
	}
	
	@Override
	public void changeUserAddress(String userName, 
								String address) {
		if (userName == null || userName.length() == 0) {
			System.out.println("DaoServiceImpl.changeUserAddress() input param is null");
			return;
		}
		
		User user = getUserByName(userName);
		if (user != null) {
			user.setAddr(address);
			//update the user into hibernate
			userDaoHbm.update(user);
		}
	}

	@Override
	public String getUserAddrByName(String userName) {
		List<User> lstUser = null;
		
		//get the user from hibernate
		lstUser = userDaoHbm.findByName(userName);
		if (lstUser == null || lstUser.size() == 0) {
			return null;
		}
		
		return lstUser.get(0).getAddr();
	}
	
	@Override
	public ShoppingPageBean getShoppingPageBean(int iCurrPage, String userName) {
		List<Shopping> lstShopping = null;
		List<Option> lstOption = null;

		ArrayList<Shopping> lstShoppingBean = new ArrayList<Shopping>();
		ShoppingPageBean clsShoppingPageBean = new ShoppingPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		int totalPrice = 0;

		//get the shopping from hibernate
		lstShopping = shoppingDaoHbm.findByUserName(userName);
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Shopping
		for (Shopping shopping : lstShopping) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				Shopping shoppingBean = new Shopping();
				shoppingBean.setId(shopping.getId());
				shoppingBean.setUserName(shopping.getUserName());
				shoppingBean.setMenuName(shopping.getMenuName());
				shoppingBean.setMenuPrice(shopping.getMenuPrice());
				shoppingBean.setAmount(shopping.getAmount());
				shoppingBean.setRemark(shopping.getRemark());
				
				lstShoppingBean.add(shoppingBean);
			}
			
			totalPrice += shopping.getAmount() * shopping.getMenuPrice();
		}
		clsShoppingPageBean.setLst(lstShoppingBean);
		clsShoppingPageBean.setTotalPrice(totalPrice);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsShoppingPageBean.setStartPage(iStartPage);
		clsShoppingPageBean.setEndPage(iEndPage);
		clsShoppingPageBean.setMaxPage(iPageNum);

		return clsShoppingPageBean;
	}
	
	@Override
	public boolean submitShoppingTrans(String userName) {
		List<Shopping> lstShopping = null;
		
		if (userName == null || userName.length() == 0) {
			System.out.println("DaoServiceImpl.submitShoppingTrans() input param is null");
			return false;
		}
		
		//get the shopping from hibernate
		lstShopping = shoppingDaoHbm.findByUserName(userName);
		
		//create a new order and save it
		Order order = new Order();
		order.setId(0);
		order.setUserName(userName);
		Date date = new Date();
		order.setTime(date);
		order.setAdminName("");
		order.setAccept(0);
		//save the order into hibernate
		orderDaoHbm.save(order);
		
		//create new orderDetail and save them
		int orderId = order.getId();
		for (Shopping shopping : lstShopping) {
			OrderDetail orderDetail = new OrderDetail();
			orderDetail.setId(0);
			orderDetail.setOrderId(orderId);
			orderDetail.setMenuName(shopping.getMenuName());
			orderDetail.setMenuPrice(shopping.getMenuPrice());
			orderDetail.setAmount(shopping.getAmount());
			orderDetail.setRemark(shopping.getRemark());
			//save the orderDetail into hibernate
			orderDetailDaoHbm.save(orderDetail);
		}
		
		//delete all shopping for this user
		for (Shopping shopping : lstShopping) {
			//delete the shopping from hibernate
			shoppingDaoHbm.delete(shopping);
		}
		
		return true;
	}
	
	@Override
	public OrderListPageBean getOrderListPageBean(int iCurrPage, String userName) {
		List<Order> lstOrder = null;
		List<Option> lstOption = null;

		ArrayList<OrderBean> lstOrderBean = new ArrayList<OrderBean>();
		OrderListPageBean clsOrderListPageBean = new OrderListPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;
		
		int iOrderNum = 0;

		//get the order from hibernate
		lstOrder = orderDaoHbm.findByUserName(userName);
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Order
		for (Order order : lstOrder) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			iOrderNum++;
			
			if (iPageNum == iCurrPage) {
				OrderBean orderBean = new OrderBean();
				orderBean.setId(order.getId());
				orderBean.setTime(order.getTime());
				orderBean.setAccept(order.getAccept());
				orderBean.setSeq(iOrderNum);
				
				lstOrderBean.add(orderBean);
			}
		}
		clsOrderListPageBean.setLst(lstOrderBean);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsOrderListPageBean.setStartPage(iStartPage);
		clsOrderListPageBean.setEndPage(iEndPage);
		clsOrderListPageBean.setMaxPage(iPageNum);

		return clsOrderListPageBean;
	}
	
	@Override
	public void deleteOrderTrans(int orderId) {
		//get the order from hibernate
		Order order = orderDaoHbm.get(orderId);
		if (order != null) {
			//delete the order into hibernate
			orderDaoHbm.delete(order);
		}
		
		//get the orderdetail from hibernate
		ArrayList<OrderDetail> lstOrderDetail  = 
				(ArrayList<OrderDetail>)orderDetailDaoHbm.findByOrderId(orderId);
		if (!lstOrderDetail.isEmpty()) {
			for (OrderDetail orderDetail : lstOrderDetail) {
				//delete the orderdetail into hibernate
				orderDetailDaoHbm.delete(orderDetail);
			}
		}

		return;
	}
	
	@Override
	public ArrayList<OrderDetail> getOrderDetailByOrderId(int orderId) {
		//get the orderdetail from hibernate
		return (ArrayList<OrderDetail>)orderDetailDaoHbm.findByOrderId(orderId);
	}

	@Override
	public boolean updateUserByName(String userOldName, 
							String userNewName, 
							String userNewPasswd, 
							String userNewAuth, 
							String userNewTel, 
							String userNewAddr) {
		User user = getUserByName(userOldName);
		if (user == null) {
			return false;
		}
		
		user.setName(userNewName);
		user.setPasswd(userNewPasswd);
		user.setAuth(userNewAuth);
		user.setTel(userNewTel);
		user.setAddr(userNewAddr);
		//update the user into hibernate
		userDaoHbm.update(user);
		
		return true;
	}
	
	@Override
	public ManUserPageBean getManUserPageBean(int iCurrPage) {
		List<User> lstUser = null;
		List<Option> lstOption = null;

		ArrayList<User> lstUserBean = new ArrayList<User>();
		ManUserPageBean clsManUserPageBean = new ManUserPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		//get all users from hibernate
		lstUser = userDaoHbm.findAll();
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Users
		for (User user : lstUser) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				User userBean = new User();
				userBean.setId(user.getId());
				userBean.setName(user.getName());
				userBean.setPasswd(user.getPasswd());
				userBean.setAuth(user.getAuth());
				userBean.setTel(user.getTel());
				userBean.setAddr(user.getAddr());
				
				lstUserBean.add(userBean);
			}
		}
		clsManUserPageBean.setLst(lstUserBean);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsManUserPageBean.setStartPage(iStartPage);
		clsManUserPageBean.setEndPage(iEndPage);
		clsManUserPageBean.setMaxPage(iPageNum);

		return clsManUserPageBean;
	}

	@Override
	public boolean updateUserById(int userOldId, 
							String userNewName, 
							String userNewPasswd, 
							String userNewAuth, 
							String userNewTel, 
							String userNewAddr) {
		//get the user from hibernate
		User user = userDaoHbm.get(userOldId);
		if (user == null) {
			return false;
		}
		
		user.setName(userNewName);
		user.setPasswd(userNewPasswd);
		user.setAuth(userNewAuth);
		user.setTel(userNewTel);
		user.setAddr(userNewAddr);
		//update the user into hibernate
		userDaoHbm.update(user);
		
		return true;
	}
	
	@Override
	public void deleteUser(int id) {
		//get the user from hibernate
		User user = userDaoHbm.get(id);
		if (user != null) {
			//delete the user from hibernate
			userDaoHbm.delete(user);
		}
		
		return;
	}
	
	@Override
	public ManOrderPageBean getManOrderPageBean(int iCurrPage) {
		List<Order> lstOrder = null;
		List<Option> lstOption = null;
		List<User> lstUser = null;

		ArrayList<ManOrderBean> lstOrderBean = new ArrayList<ManOrderBean>();
		ManOrderPageBean clsManOrderPageBean = new ManOrderPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		//get all orders from hibernate
		lstOrder = orderDaoHbm.findAll();
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Orders
		for (Order order : lstOrder) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				//get user from hibernate
				lstUser = userDaoHbm.findByName(order.getUserName());
				if (lstUser.size() == 0) {
					continue;
				}
				
				ManOrderBean orderBean = new ManOrderBean();
				orderBean.setId(order.getId());
				orderBean.setUserName(order.getUserName());
				orderBean.setTime(order.getTime());
				orderBean.setAdminName(order.getAdminName());
				orderBean.setAccept(order.getAccept());
				orderBean.setTel(lstUser.get(0).getTel());
				orderBean.setAddr(lstUser.get(0).getAddr());
				
				lstOrderBean.add(orderBean);
			}
		}
		clsManOrderPageBean.setLst(lstOrderBean);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsManOrderPageBean.setStartPage(iStartPage);
		clsManOrderPageBean.setEndPage(iEndPage);
		clsManOrderPageBean.setMaxPage(iPageNum);

		return clsManOrderPageBean;
	}

	@Override
	public boolean updateOrderAccept(int id, String adminName, boolean isAccept) {
		//get the order from hibernate
		Order order = orderDaoHbm.get(id);
		if (order == null) {
			return false;
		}
		
		if(isAccept) {
			order.setAccept(1);
		}else {
			order.setAccept(0);
		}
		order.setAdminName(adminName);
		//update the order into hibernate
		orderDaoHbm.update(order);
		
		return true;
	}

	@Override
	public boolean insertOrderDetail(int orderId,
									String menu, 					
									int price, 
									int amount, 
									String remark) {
		List<OrderDetail> lstOrderDetail = null;
		
		//get the orderdetail from hibernate
		lstOrderDetail = orderDetailDaoHbm.find(orderId, menu);
		if (lstOrderDetail == null || lstOrderDetail.size() != 0) {
			return false;
		}
		
		OrderDetail orderDetail = new OrderDetail();
		orderDetail.setOrderId(orderId);
		orderDetail.setMenuName(menu);
		orderDetail.setMenuPrice(price);
		orderDetail.setAmount(amount);
		orderDetail.setRemark(remark);
		//save the orderdetail into hibernate
		orderDetailDaoHbm.save(orderDetail);
		
		return true;
	}

	@Override
	public boolean updateOrderDetailById(int id, 
										int orderId,
										String menu, 					
										int price, 
										int amount, 
										String remark) {
		//get the orderdetail from hibernate
		OrderDetail orderDetail = orderDetailDaoHbm.get(id);
		if (orderDetail == null) {
			return false;
		}
		
		orderDetail.setOrderId(orderId);
		orderDetail.setMenuName(menu);
		orderDetail.setMenuPrice(price);
		orderDetail.setAmount(amount);
		orderDetail.setRemark(remark);
		//update the orderdetail into hibernate
		orderDetailDaoHbm.update(orderDetail);
		
		return true;
	}
	
	@Override
	public void deleteOrderDetail(int id) {
		//get the orderdetail from hibernate
		OrderDetail orderDetail = orderDetailDaoHbm.get(id);
		if (orderDetail != null) {
			//delete the orderdetail from hibernate
			orderDetailDaoHbm.delete(orderDetail);
		}
		
		return;
	}
	
	@Override
	public ManPicPageBean getManPicPageBean(int iCurrPage) {
		List<Option> lstOption = null;

		ManPicBean manPicBean;
		ArrayList<ManPicBean> lstPicBean = new ArrayList<ManPicBean>();
		ManPicPageBean clsManPicPageBean = new ManPicPageBean();

		String optionName = "idx_menu_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		int iMaxPicOneLine = 4;
		int iPicNum = 0;

		// get the idx_menu_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		// tree the pic directory
		@SuppressWarnings("deprecation")
		String rootDir = ServletActionContext.getRequest().getRealPath("/");
		File f = new File(rootDir + defaultPicSavePath);
		if (f.isDirectory()) {
			File[] t = f.listFiles();

			// iterator the File
			for (int i = 0; i < t.length; i++) {
				iPicNum++;

				if (iPicNum > iMaxPicOneLine) {
					iPicNum = 0;
					iLineNum++;
				}
				if (iLineNum > iMaxLineOnePage) {
					iLineNum = 1;
					iPageNum++;
				}

				if (iPageNum == iCurrPage) {
					String name = t[i].getName();
					String[] pp = name.split("\\.");
					if (pp.length != 2) {
						continue;
					}

					manPicBean = new ManPicBean();
					manPicBean.setName(pp[0]);
					manPicBean.setAddr(defaultPicSavePath + name);

					lstPicBean.add(manPicBean);
				}
			}
			clsManPicPageBean.setLst(lstPicBean);

			// calc the startpage and endpage
			if (iPageNum <= defaultMaxPagination) {
				iStartPage = 1;
				iEndPage = iPageNum;
			} else {
				if (iCurrPage > defaultMaxPagination / 2) {
					iStartPage = iCurrPage - defaultMaxPagination / 2;
				} else {
					iStartPage = 1;
				}
	
				if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
					iEndPage = iCurrPage + defaultMaxPagination / 2;
				} else {
					iEndPage = iPageNum;
				}
			}
			clsManPicPageBean.setStartPage(iStartPage);
			clsManPicPageBean.setEndPage(iEndPage);
			clsManPicPageBean.setMaxPage(iPageNum);
		}

		/*
		//get the menu from redis
		lstMenu = menuDaoRds.findAll();
		for (Menu menu : lstMenu) {
			//exclude the menu without picture address
			if (menu.getPicture().isEmpty()) {
				continue;
			}
			
			iPicNum++;

			if (iPicNum > iMaxPicOneLine) {
				iPicNum = 0;
				iLineNum++;
			}
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}

			if (iPageNum == iCurrPage) {
				manPicBean = new ManPicBean();
				manPicBean.setName(menu.getName());
				manPicBean.setAddr(menu.getPicture());

				lstPicBean.add(manPicBean);
			}
		}
		*/

		return clsManPicPageBean;
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void deletePic(String name, String addr) {
		ArrayList<Menu> lstNewMenu = new ArrayList<Menu>();
		
		//move the pic file to del directory
		String rootDir = ServletActionContext.getRequest().getRealPath("/");
		String picDir = rootDir + addr;
		String[] pp = addr.split("/");
		String picDelDir = rootDir + defaultPicDelPath + pp[2];
		String cmd = "mv " + picDir + " " + picDelDir;
		try {
			Runtime.getRuntime().exec(cmd);
		} catch (IOException e1) {
			return;
		}
		
		//find menu list from redis
		ArrayList<Menu> lstOldMenu = (ArrayList<Menu>)menuDaoRds.findByPicAddr(addr);
		if (lstOldMenu.size() != 0) {
			lstNewMenu = (ArrayList<Menu>)menuDaoRds.findByPicAddr(addr);
			for (Menu menu : lstNewMenu) {
				//clear the pic addr for all menu
				menu.setPicture("");

				//update the new menu into redis
				menuDaoRds.update(menu);
			}
		}
		
		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.UPDATE_MENU_LST);
		oi.setLstOldMenu(lstOldMenu);
		oi.setLstNewMenu(lstNewMenu);
		oi.setPicDir(picDir);
		oi.setPicDelDir(picDelDir);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.deletePic() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				for (Menu menu : lstOldMenu) {
					menuDaoRds.update(menu);
				}
				
				//restore the pic file from del directory
				cmd = "mv " + picDelDir + " " + picDir;
				Runtime.getRuntime().exec(cmd);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.deletePic() menuDaoRds.update() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}

		return;
	}

	@Override
	public void uploadPic(String uploadFileName, File upload) throws Exception {
		@SuppressWarnings("deprecation")
		String rootDir = ServletActionContext.getRequest().getRealPath("/");
		String outfileName = rootDir + defaultPicSavePath + uploadFileName;
		FileOutputStream fos = new FileOutputStream(outfileName);

		FileInputStream fis = new FileInputStream(upload);

		byte[] buffer = new byte[1024];
		int len = 0;
		while ((len = fis.read(buffer)) > 0) {
			fos.write(buffer, 0, len);
		}

		fos.close();
		fis.close();
		return;
	}
	
	@Override
	public ManMenuPageBean getManMenuPageBean(int iCurrPage) {
		List<Menu> lstMenu = null;
		List<MenuType> lstMenuType = null;
		List<Option> lstOption = null;

		ArrayList<Menu> lstMenuBean = new ArrayList<Menu>();
		ArrayList<MenuType> lstMenuTypeBean = new ArrayList<MenuType>();
		ArrayList<ManPicBean> lstPicBean = new ArrayList<ManPicBean>();
		ManMenuPageBean clsManMenuPageBean = new ManMenuPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		//get all menus from redis
		lstMenu = menuDaoRds.findAll();

		//get all menutypes from redis
		lstMenuType = menuTypeDaoRds.findAll();
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the Menus
		for (Menu menu : lstMenu) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				Menu menuBean = new Menu();
				menuBean.setId(menu.getId());
				menuBean.setName(menu.getName());
				menuBean.setPicture(menu.getPicture());
				menuBean.setPrice(menu.getPrice());
				menuBean.setType(menu.getType());
				
				lstMenuBean.add(menuBean);
			}
		}
		clsManMenuPageBean.setLst(lstMenuBean);

		//iterator the MenuTypes
		for (MenuType menuType : lstMenuType) {
			MenuType menuTypeBean = new MenuType();
			menuTypeBean.setId(menuType.getId());
			menuTypeBean.setName(menuType.getName());

			lstMenuTypeBean.add(menuTypeBean);
		}
		clsManMenuPageBean.setLst2(lstMenuTypeBean);

		@SuppressWarnings("deprecation")
		String rootDir = ServletActionContext.getRequest().getRealPath("/");
		File f = new File(rootDir + defaultPicSavePath);
		if (f.isDirectory()) {
			File[] t = f.listFiles();

			// iterator the File
			for (int i = 0; i < t.length; i++) {
				String name = t[i].getName();
				String[] pp = name.split("\\.");
				if (pp.length != 2) {
					continue;
				}

				ManPicBean manPicBean = new ManPicBean();
				manPicBean.setName(pp[0]);
				manPicBean.setAddr(defaultPicSavePath + name);

				lstPicBean.add(manPicBean);
			}
			clsManMenuPageBean.setLst3(lstPicBean);
		}

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsManMenuPageBean.setStartPage(iStartPage);
		clsManMenuPageBean.setEndPage(iEndPage);
		clsManMenuPageBean.setMaxPage(iPageNum);

		return clsManMenuPageBean;
	}

	@Override
	public boolean insertMenu(String menuName, 
							Integer menuPrice, 
							String menuPic, 
							Integer menuType) {
		//get the menu from redis
		List<Menu> lstMenu = menuDaoRds.findByName(menuName);
		if (lstMenu != null && lstMenu.size() != 0 && lstMenu.get(0) != null) {
			return false;
		}
		
		Menu newMenu = new Menu();
		newMenu.setName(menuName);
		newMenu.setPrice(menuPrice);
		newMenu.setPicture(menuPic);
		MenuType menuType2 = menuTypeDaoRds.get(menuType);
		newMenu.setType(menuType2);
		newMenu.setId(0);
		//save the menu into redis
		menuDaoRds.save(newMenu);
		
		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.SAVE_MENU);
		oi.setNewMenu(newMenu);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.insertMenu() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuDaoRds.delete(newMenu);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.insertMenu() menuDaoRds.delete() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}

		return true;
	}

	@Override
	public boolean updateMenuById(int menuOldId, 
								String menuNewName, 
								Integer menuNewPrice, 
								String menuNewPic, 
								Integer menuNewType) {
		//get the menu from redis
		Menu oldMenu = menuDaoRds.get(menuOldId);
		if (oldMenu == null) {
			return false;
		}
		
		Menu newMenu = new Menu();
		newMenu.setId(oldMenu.getId());
		newMenu.setName(menuNewName);
		newMenu.setPrice(menuNewPrice);
		newMenu.setPicture(menuNewPic);
		MenuType menuType = menuTypeDaoRds.get(menuNewType);
		newMenu.setType(menuType);
		//update the menu into redis
		menuDaoRds.update(newMenu);

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.UPDATE_MENU);
		oi.setOldMenu(oldMenu);
		oi.setNewMenu(newMenu);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.updateMenuById() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuDaoRds.update(oldMenu);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.updateMenuById() menuDaoRds.update() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return true;
	}
	
	@Override
	public void deleteMenu(int id) {
		//get the menu from redis
		Menu oldMenu = menuDaoRds.get(id);
		if (oldMenu != null) {
			//delete the menu from redis
			menuDaoRds.delete(oldMenu);
		}

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.DELETE_MENU);
		oi.setOldMenu(oldMenu);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.deleteMenu() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuDaoRds.save(oldMenu);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.deleteMenu() menuDaoRds.save() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return;
	}
	
	@Override
	public ManMenuTypePageBean getManMenuTypePageBean(int iCurrPage) {
		List<MenuType> lstMenuType = null;
		List<Option> lstOption = null;

		ArrayList<MenuType> lstMenuTypeBean = new ArrayList<MenuType>();
		ManMenuTypePageBean clsManMenuTypePageBean = new ManMenuTypePageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		//get all menutypes from redis
		lstMenuType = menuTypeDaoRds.findAll();
		
		//get the tbl_page_lines from redis
		lstOption = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption.get(0).getValue());

		//iterator the MenuTypes
		for (MenuType menuType : lstMenuType) {
			//其他类型菜品永久保留，不提供管理界面修改
			if (menuType.getId() == 1)
				continue;
			
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				MenuType menuTypeBean = new MenuType();
				menuTypeBean.setId(menuType.getId());
				menuTypeBean.setName(menuType.getName());
				
				lstMenuTypeBean.add(menuTypeBean);
			}
		}
		clsManMenuTypePageBean.setLst(lstMenuTypeBean);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsManMenuTypePageBean.setStartPage(iStartPage);
		clsManMenuTypePageBean.setEndPage(iEndPage);
		clsManMenuTypePageBean.setMaxPage(iPageNum);

		return clsManMenuTypePageBean;
	}

	@Override
	public boolean insertMenuType(String menuTypeName) {
		//get the menutype from redis
		List<MenuType> lstMenuType = menuTypeDaoRds.findByName(menuTypeName);
		if (lstMenuType != null && lstMenuType.size() != 0 && lstMenuType.get(0) != null) {
			return false;
		}
		
		MenuType newMenuType = new MenuType();
		newMenuType.setName(menuTypeName);
		newMenuType.setId(0);
		//save the menutype into redis
		menuTypeDaoRds.save(newMenuType);
		
		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.SAVE_MENU_TYPE);
		oi.setNewMenuType(newMenuType);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.insertMenuType() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuTypeDaoRds.delete(newMenuType);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.insertMenuType() menuTypeDaoRds.delete() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}

		return true;
	}

	@Override
	public boolean updateMenuTypeById(int menuTypeOldId, 
								String menuTypeNewName) {
		//get the menutype from redis
		MenuType oldMenuType = menuTypeDaoRds.get(menuTypeOldId);
		if (oldMenuType == null) {
			return false;
		}
		
		MenuType newMenuType = new MenuType();
		newMenuType.setId(oldMenuType.getId());
		newMenuType.setName(menuTypeNewName);
		//update the menutype into redis
		menuTypeDaoRds.update(newMenuType);

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.UPDATE_MENU_TYPE);
		oi.setOldMenuType(oldMenuType);
		oi.setNewMenuType(newMenuType);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.updateMenuTypeById() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuTypeDaoRds.update(oldMenuType);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.updateMenuTypeById() menuTypeDaoRds.update() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return true;
	}
	
	@Override
	public void deleteMenuType(int id) {
		List<Menu> lstOldMenu = null;
		
		//get the menutype from redis
		MenuType oldMenuType = menuTypeDaoRds.get(id);
		if (oldMenuType != null) {
			//get all menus from redis
			lstOldMenu = menuDaoRds.findByMenuTypeId(String.valueOf(oldMenuType.getId()));

			//get the default menutype
			MenuType newMenuType = menuTypeDaoRds.get(1);
			if (newMenuType == null) {
				System.out.println("DaoServiceImpl.deleteMenuType() menuTypeDaoRds.get(1) fail ");
				return;
			}

			//update all menus to new type
			//delete the menutype from redis
			transDaoRds.deleteMenuType(lstOldMenu, oldMenuType, newMenuType);
		}

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.DELETE_MENU_TYPE);
		oi.setOldMenuType(oldMenuType);
		oi.setLstOldMenu((ArrayList<Menu>)lstOldMenu);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.deleteMenuType() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				menuTypeDaoRds.save(oldMenuType);
				
				List<MenuType> lstMenuType = menuTypeDaoRds.findByName(oldMenuType.getName());
				if (lstMenuType != null && lstMenuType.size() != 0 && lstMenuType.get(0) != null) {
					for (Menu menu : lstOldMenu) {
						menu.setType(lstMenuType.get(0));
						menuDaoRds.update(menu);
					}
				}
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.deleteMenuType() menuTypeDaoRds.save() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return;
	}
	
	@Override
	public ManOptionPageBean getManOptionPageBean(int iCurrPage) {
		List<Option> lstOption = null;
		List<Option> lstOption2 = null;

		ArrayList<Option> lstOptionBean = new ArrayList<Option>();
		ManOptionPageBean clsManOptionPageBean = new ManOptionPageBean();

		String optionName = "tbl_page_lines";

		int iStartPage = 1;
		int iEndPage = 1;
		int iPageNum = 1;

		int iMaxLineOnePage = 0;
		int iLineNum = 0;

		//get all options from redis
		lstOption = optionDaoRds.findAll();
		
		//get the tbl_page_lines from redis
		lstOption2 = optionDaoRds.findByName(optionName);
		iMaxLineOnePage = Integer.valueOf(lstOption2.get(0).getValue());

		//iterator the Option
		for (Option option : lstOption) {
			iLineNum++;
			if (iLineNum > iMaxLineOnePage) {
				iLineNum = 1;
				iPageNum++;
			}
			
			if (iPageNum == iCurrPage) {
				Option optionBean = new Option();
				optionBean.setId(option.getId());
				optionBean.setName(option.getName());
				optionBean.setValue(option.getValue());
				
				lstOptionBean.add(optionBean);
			}
		}
		clsManOptionPageBean.setLst(lstOptionBean);

		// calc the startpage and endpage
		if (iPageNum <= defaultMaxPagination) {
			iStartPage = 1;
			iEndPage = iPageNum;
		} else {
			if (iCurrPage > defaultMaxPagination / 2) {
				iStartPage = iCurrPage - defaultMaxPagination / 2;
			} else {
				iStartPage = 1;
			}

			if (iPageNum >= (iCurrPage + defaultMaxPagination / 2)) {
				iEndPage = iCurrPage + defaultMaxPagination / 2;
			} else {
				iEndPage = iPageNum;
			}
		}
		clsManOptionPageBean.setStartPage(iStartPage);
		clsManOptionPageBean.setEndPage(iEndPage);
		clsManOptionPageBean.setMaxPage(iPageNum);

		return clsManOptionPageBean;
	}

	@Override
	public boolean insertOption(String optionName, 
							String optionValue) {
		//get the option from redis
		List<Option> lstOption = optionDaoRds.findByName(optionName);
		if (lstOption != null && lstOption.size() != 0 && lstOption.get(0) != null) {
			return false;
		}
		
		Option newOption = new Option();
		newOption.setName(optionName);
		newOption.setValue(optionValue);
		newOption.setId(0);
		//save the option into redis
		optionDaoRds.save(newOption);
		
		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.SAVE_OPTION);
		oi.setNewOption(newOption);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.insertOption() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				optionDaoRds.delete(newOption);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.insertOption() optionDaoRds.delete() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}

		return true;
	}

	@Override
	public boolean updateOptionById(int optionOldId, 
								String optionNewName, 
								String optionNewValue) {
		//get the option from redis
		Option oldOption = optionDaoRds.get(optionOldId);
		if (oldOption == null) {
			return false;
		}
		
		Option newOption = new Option();
		newOption.setId(oldOption.getId());
		newOption.setName(optionNewName);
		newOption.setValue(optionNewValue);
		//update the option into redis
		optionDaoRds.update(newOption);

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.UPDATE_OPTION);
		oi.setOldOption(oldOption);
		oi.setNewOption(newOption);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.updateOptionById() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				optionDaoRds.update(oldOption);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.updateOptionById() optionDaoRds.update() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return true;
	}
	
	@Override
	public void deleteOption(int id) {
		//get the option from redis
		Option oldOption = optionDaoRds.get(id);
		if (oldOption != null) {
			//delete the option from redis
			optionDaoRds.delete(oldOption);
		}

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.DELETE_OPTION);
		oi.setOldOption(oldOption);
		try {
			//put this msg into the queue
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.deleteOption() q.put() catch exception: " + e.getMessage());
			try {
				//if fail, need restore the redis
				optionDaoRds.save(oldOption);
			}catch (Exception e2) {
				System.out.println("DaoServiceImpl.deleteOption() optionDaoRds.save() catch exception: " + e.getMessage());
				//redis and mysql will be inconsistency in here
			}
		}
		
		return;
	}

	public void init() {
		//create the queue
		q = new LinkedBlockingQueue<OptInfo>();
		
		//create the thread
		Consumer worker = new Consumer();
		thd = new Thread(worker, "Consumer");
		thd.start();
	}
	
	public void destory() {
		//close the thread
		thd.interrupt();

		OptInfo oi = new OptInfo();
		oi.setOpt(OPT.INVALID);
		try {
			q.put(oi);
		}catch (InterruptedException e) {
			System.out.println("DaoServiceImpl.destory() q.put() catch exception: " + e.getMessage());
		}
	}

	private class OptInfo {
		private OPT opt;
		
		private ArrayList<Menu> lstOldMenu;
		private ArrayList<Menu> lstNewMenu;
		private String picDir;
		private String picDelDir;
		
		private Menu oldMenu;
		private Menu newMenu;

		private MenuType oldMenuType;
		private MenuType newMenuType;

		private Option oldOption;
		private Option newOption;

		public OPT getOpt() {
			return opt;
		}
		public void setOpt(OPT opt) {
			this.opt = opt;
		}
		public ArrayList<Menu> getLstOldMenu() {
			return lstOldMenu;
		}
		public void setLstOldMenu(ArrayList<Menu> lstOldMenu) {
			this.lstOldMenu = lstOldMenu;
		}
		public ArrayList<Menu> getLstNewMenu() {
			return lstNewMenu;
		}
		public void setLstNewMenu(ArrayList<Menu> lstNewMenu) {
			this.lstNewMenu = lstNewMenu;
		}
		public String getPicDir() {
			return picDir;
		}
		public void setPicDir(String picDir) {
			this.picDir = picDir;
		}
		public String getPicDelDir() {
			return picDelDir;
		}
		public void setPicDelDir(String picDelDir) {
			this.picDelDir = picDelDir;
		}
		public Menu getOldMenu() {
			return oldMenu;
		}
		public void setOldMenu(Menu oldMenu) {
			this.oldMenu = oldMenu;
		}
		public Menu getNewMenu() {
			return newMenu;
		}
		public void setNewMenu(Menu newMenu) {
			this.newMenu = newMenu;
		}
		public MenuType getOldMenuType() {
			return oldMenuType;
		}
		public void setOldMenuType(MenuType oldMenuType) {
			this.oldMenuType = oldMenuType;
		}
		public MenuType getNewMenuType() {
			return newMenuType;
		}
		public void setNewMenuType(MenuType newMenuType) {
			this.newMenuType = newMenuType;
		}
		public Option getOldOption() {
			return oldOption;
		}
		public void setOldOption(Option oldOption) {
			this.oldOption = oldOption;
		}
		public Option getNewOption() {
			return newOption;
		}
		public void setNewOption(Option newOption) {
			this.newOption = newOption;
		}
	}

	private class Consumer implements Runnable {
		@Override
		public void run() {
			OptInfo oi;
			
			while(!Thread.interrupted()) {
				try {
					oi = q.take();
				}catch (InterruptedException e) {
					System.out.println("DaoServiceImpl.Consumer.run() q.take() catch exception: " + e.getMessage());
					continue;
				}
				
				if (oi.getOpt() == OPT.INVALID) {
					continue;
				}else if (oi.getOpt() == OPT.UPDATE_MENU_LST) {
					ArrayList<Menu> lstOldMenu = oi.getLstOldMenu();
					ArrayList<Menu> lstNewMenu = oi.getLstNewMenu();
					String picDir = oi.getPicDir();
					String picDelDir = oi.getPicDelDir();
					
					try {
						//update the menu into hibernate
						for (Menu menu : lstNewMenu) {
							menuDaoHbm.update(menu);
						}
					
						//delete the pic file from del directory
						String cmd = "rm -fr " + picDelDir;
						Runtime.getRuntime().exec(cmd);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuDaoHbm.update() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							for (Menu menu : lstOldMenu) {
								menuDaoRds.update(menu);
							}
							
							//restore the pic file from del directory
							String cmd = "mv " + picDelDir + " " + picDir;
							Runtime.getRuntime().exec(cmd);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuDaoRds.update() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.SAVE_MENU) {
					Menu newMenu = oi.getNewMenu();
					
					try {
						//save the menu into hibernate
						menuDaoHbm.save(newMenu);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuDaoHbm.save() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuDaoRds.delete(newMenu);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuDaoRds.delete() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.UPDATE_MENU) {
					Menu oldMenu = oi.getOldMenu();
					Menu newMenu = oi.getNewMenu();
					
					try {
						//update the menu into hibernate
						menuDaoHbm.update(newMenu);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuDaoHbm.update() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuDaoRds.update(oldMenu);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuDaoRds.update() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.DELETE_MENU) {
					Menu oldMenu = oi.getOldMenu();
					
					try {
						//delete the menu into hibernate
						menuDaoHbm.delete(oldMenu);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuDaoHbm.delete() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuDaoRds.save(oldMenu);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuDaoRds.save() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.SAVE_MENU_TYPE) {
					MenuType newMenuType = oi.getNewMenuType();
					
					try {
						//save the menutype into hibernate
						menuTypeDaoHbm.save(newMenuType);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoHbm.save() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuTypeDaoRds.delete(newMenuType);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoRds.delete() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.UPDATE_MENU_TYPE) {
					MenuType oldMenuType = oi.getOldMenuType();
					MenuType newMenuType = oi.getNewMenuType();
					
					try {
						//update the menutype into hibernate
						menuTypeDaoHbm.update(newMenuType);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoHbm.update() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuTypeDaoRds.update(oldMenuType);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoRds.update() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.DELETE_MENU_TYPE) {
					MenuType oldMenuType = oi.getOldMenuType();
					ArrayList<Menu> lstOldMenu = oi.getLstOldMenu();
					
					try {
						//get all menus from redis
						List<Menu> lstMenu = menuDaoHbm.findByMenuTypeId(String.valueOf(oldMenuType.getId()));
						
						//update all menus to new type
						MenuType newMenuType = menuTypeDaoHbm.get(1);
						if (newMenuType == null) {
							System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoHbm.get(1) fail ");
							return;
						}
						for (Menu menu : lstMenu) {
							menu.setType(newMenuType);
							menuDaoHbm.update(menu);
						}

						//delete the menutype into hibernate
						menuTypeDaoHbm.delete(oldMenuType);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoHbm.delete() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							menuTypeDaoRds.save(oldMenuType);
							
							List<MenuType> lstMenuType = menuTypeDaoRds.findByName(oldMenuType.getName());
							if (lstMenuType != null && lstMenuType.size() != 0 && lstMenuType.get(0) != null) {
								for (Menu menu : lstOldMenu) {
									menu.setType(lstMenuType.get(0));
									menuDaoRds.update(menu);
								}
							}
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() menuTypeDaoRds.save() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.SAVE_OPTION) {
					Option newOption = oi.getNewOption();
					
					try {
						//save the option into hibernate
						optionDaoHbm.save(newOption);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() optionDaoHbm.save() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							optionDaoRds.delete(newOption);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() optionDaoRds.delete() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.UPDATE_OPTION) {
					Option oldOption = oi.getOldOption();
					Option newOption = oi.getNewOption();
					
					try {
						//update the option into hibernate
						optionDaoHbm.update(newOption);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() optionDaoHbm.update() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							optionDaoRds.update(oldOption);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() optionDaoRds.update() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}else if (oi.getOpt() == OPT.DELETE_OPTION) {
					Option oldOption = oi.getOldOption();
					
					try {
						//delete the option into hibernate
						optionDaoHbm.delete(oldOption);
					}catch (Exception e) {
						System.out.println("DaoServiceImpl.Consumer.run() optionDaoHbm.delete() catch exception: " + e.getMessage());
						try {
							//if fail, need restore the redis
							optionDaoRds.save(oldOption);
						}catch (Exception e2) {
							System.out.println("DaoServiceImpl.Consumer.run() optionDaoRds.save() catch exception: " + e.getMessage());
							//redis and mysql will be inconsistency in here
						}
					}
				}
			}
			
			return;
		}
	}

	public MenuDao getMenuDaoHbm() {
		return menuDaoHbm;
	}

	public void setMenuDaoHbm(MenuDao menuDaoHbm) {
		this.menuDaoHbm = menuDaoHbm;
	}

	public MenuTypeDao getMenuTypeDaoHbm() {
		return menuTypeDaoHbm;
	}

	public void setMenuTypeDaoHbm(MenuTypeDao menuTypeDaoHbm) {
		this.menuTypeDaoHbm = menuTypeDaoHbm;
	}

	public UserDao getUserDaoHbm() {
		return userDaoHbm;
	}

	public void setUserDaoHbm(UserDao userDaoHbm) {
		this.userDaoHbm = userDaoHbm;
	}

	public OptionDao getOptionDaoHbm() {
		return optionDaoHbm;
	}

	public void setOptionDaoHbm(OptionDao optionDaoHbm) {
		this.optionDaoHbm = optionDaoHbm;
	}
	
	public ShoppingDao getShoppingDaoHbm() {
		return shoppingDaoHbm;
	}

	public void setShoppingDaoHbm(ShoppingDao shoppingDaoHbm) {
		this.shoppingDaoHbm = shoppingDaoHbm;
	}

	public OrderDao getOrderDaoHbm() {
		return orderDaoHbm;
	}

	public void setOrderDaoHbm(OrderDao orderDaoHbm) {
		this.orderDaoHbm = orderDaoHbm;
	}

	public OrderDetailDao getOrderDetailDaoHbm() {
		return orderDetailDaoHbm;
	}

	public void setOrderDetailDaoHbm(OrderDetailDao orderDetailDaoHbm) {
		this.orderDetailDaoHbm = orderDetailDaoHbm;
	}

	public MenuDao getMenuDaoRds() {
		return menuDaoRds;
	}

	public void setMenuDaoRds(MenuDao menuDaoRds) {
		this.menuDaoRds = menuDaoRds;
	}

	public MenuTypeDao getMenuTypeDaoRds() {
		return menuTypeDaoRds;
	}

	public void setMenuTypeDaoRds(MenuTypeDao menuTypeDaoRds) {
		this.menuTypeDaoRds = menuTypeDaoRds;
	}

	public OptionDao getOptionDaoRds() {
		return optionDaoRds;
	}

	public void setOptionDaoRds(OptionDao optionDaoRds) {
		this.optionDaoRds = optionDaoRds;
	}

	public TransDao getTransDaoRds() {
		return transDaoRds;
	}

	public void setTransDaoRds(TransDao transDaoRds) {
		this.transDaoRds = transDaoRds;
	}
}
