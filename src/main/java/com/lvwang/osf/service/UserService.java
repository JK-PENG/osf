package com.lvwang.osf.service;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.lvwang.osf.dao.UserDAO;
import com.lvwang.osf.model.User;
import com.lvwang.osf.util.CipherUtil;
import com.lvwang.osf.util.Property;

@Service
public class UserService {


	public static final int USERNAME_MAXLEN = 20;
	public static final int STATUS_USER_NORMAL = 0;				//正常
	public static final int STATUS_USER_INACTIVE = 1;			//待激活
	public static final int STATUS_USER_LOCK = 2;				//锁定
	public static final int STATUS_USER_CANCELLED = 3;			//注销
	
	public static final String DEFAULT_USER_AVATAR = "default-avatar.jpg";
	
	@Autowired
	@Qualifier("userDao")
	private UserDAO userDao;
	

		
	private boolean ValidateEmail(String email) {
		boolean result = true;
		try {
			InternetAddress emailAddr = new InternetAddress(email);
			emailAddr.validate();
		} catch (AddressException e) {
			result = false;
		}
		return result;
	}
	
	public String confirmPwd(String user_name, String user_pwd) {
		if(user_pwd == null || user_name.length() == 0)
			return Property.ERROR_PWD_EMPTY;
		String pwd = userDao.getPwdByUsername(user_name);
		if(pwd.equals(user_pwd)) 
			return null;
		else
			return Property.ERROR_PWD_DIFF;
			
	}
	
	public User findByUsername(String username) {
		return userDao.getUserByUsername(username);
	}
	
	public User findByEmail(String email) {
		return userDao.getUserByEmail(email);
	}
	
	public User findById(int id) {
		User user = userDao.getUserByID(id);
		addAvatar(user);
		return user;
	}
	
	private void addAvatar(User user) {
		user.setUser_avatar(Property.IMG_BASE_URL+user.getUser_avatar());
	}
	
	public Map<String, Object> login(String email, String password) {
		Map<String, Object> ret = new HashMap<String, Object>();
		//1 empty check
		if(email == null || email.length() <= 0) {
			ret.put("status", Property.ERROR_EMAIL_EMPTY);
			return ret;
		}
			
		if(password == null || password.length() <= 0){
			ret.put("status", Property.ERROR_PWD_EMPTY);
			return ret;
		}
			
		
		//2 ValidateEmail 
		if(!ValidateEmail(email)) {
			ret.put("status", Property.ERROR_EMAIL_FORMAT);
			return ret;
		}

		//3 email exist?
		User user = findByEmail(email);
		if(user == null) {
			ret.put("status", Property.ERROR_EMAIL_NOT_REG);
			return ret;
		}
		else {
			//4 check user status
			if(STATUS_USER_NORMAL != user.getUser_status()) {
				ret.put("status", user.getUser_status());
				return ret;
			}
		}
		
		//5 password validate
		if(!CipherUtil.validatePassword(user.getUser_pwd(), password)) {
			ret.put("status", Property.ERROR_PWD_DIFF);
			return ret;
		}
		ret.put("status", Property.SUCCESS_ACCOUNT_LOGIN);
		ret.put("user", user);
		return ret;
	}
	
	
	@SuppressWarnings("deprecation")
	public String register(String username, String email, String password, String conformPwd, Map<String, String> map) {
		//1 empty check
		if(email == null || email.length() <= 0)
			return Property.ERROR_EMAIL_EMPTY;
		else{
			//4 ValidateEmail
			if(!ValidateEmail(email))
				return Property.ERROR_EMAIL_FORMAT;
			
			//5 email exist?
			User user = findByEmail(email);
			if(user != null) {
							
				//6 user status check
				if(STATUS_USER_NORMAL == user.getUser_status())
					return Property.ERROR_ACCOUNT_EXIST;
				else if(STATUS_USER_INACTIVE == user.getUser_status()){
					map.put("activationKey", URLEncoder.encode(user.getUser_activationKey()));
					return Property.ERROR_ACCOUNT_INACTIVE;
				}
				else if(STATUS_USER_LOCK == user.getUser_status())
					return Property.ERROR_ACCOUNT_LOCK;
				else if(STATUS_USER_CANCELLED == user.getUser_status()) 
					return Property.ERROR_ACCOUNT_CANCELLED;
			}			
		}
		
		if(username == null || username.length() == 0) 
			return Property.ERROR_USERNAME_EMPTY;
		else {
			//username exist check
			if(findByUsername(username) != null) {
				return Property.ERROR_USERNAME_EXIST;
			}
		}
		
		
		if(password == null || password.length() <= 0)
			return Property.ERROR_PWD_EMPTY;
		else {
			//3 password format validate
			String vpf_rs = CipherUtil.validatePasswordFormat(password);
			if(vpf_rs != Property.SUCCESS_PWD_FORMAT)
				return vpf_rs;
		}
		if(conformPwd == null || conformPwd.length() <= 0)
			return Property.ERROR_CFMPWD_EMPTY;
				
		//2 pwd == conformPwd ?
		if(!password.equals(conformPwd))
			return Property.ERROR_CFMPWD_NOTAGREE;
					
		
		User user = new User();
		user.setUser_name(username);
		user.setUser_pwd(CipherUtil.generatePassword(password));
		user.setUser_email(email);
		user.setUser_status(STATUS_USER_INACTIVE);
		user.setUser_avatar(DEFAULT_USER_AVATAR);
		String activationKey = CipherUtil.generateActivationUrl(email, password);
		user.setUser_activationKey(activationKey);
		int id =userDao.save(user);
		
		map.put("id", String.valueOf(id));
		map.put("activationKey", URLEncoder.encode(activationKey));
		return Property.SUCCESS_ACCOUNT_REG;
		
	}
	
	public Map<String, Object> updateActivationKey(String email){
		//1 check user status
		User user = findByEmail(email);
		String status = null;
		Map<String, Object> map = new HashMap<String, Object>();
		if(user == null){
			status = Property.ERROR_EMAIL_NOT_REG;
		}
		
		if(STATUS_USER_INACTIVE == user.getUser_status()){
			//2 gen activation key
			String activationKey = CipherUtil.generateActivationUrl(email, new Date().toString());
			userDao.updateActivationKey(user.getId(), activationKey);
			status = Property.SUCCESS_ACCOUNT_ACTIVATION_KEY_UPD;
			map.put("activationKey", activationKey);
		} else {
			if(STATUS_USER_NORMAL == user.getUser_status())
				status = Property.ERROR_ACCOUNT_EXIST; //已激活
			else if(STATUS_USER_CANCELLED == user.getUser_status()) 
				status = Property.ERROR_ACCOUNT_CANCELLED;
			
			status = Property.ERROR_ACCOUNT_ACTIVATION;
		}
		map.put("status", status);
		return map;
	}
	
	public String activateUser(String email, String key) {
		User user = findByEmail(email);
		if(user == null)
			return Property.ERROR_ACCOUNT_ACTIVATION_NOTEXIST;
		else {
			
			if(user.getUser_status() == STATUS_USER_INACTIVE ){
				if(user.getUser_activationKey().equals(key)){
					user.setUser_activationKey(null);
					user.setUser_status(STATUS_USER_NORMAL);
					
					userDao.activateUser(user);
				}else {
					return Property.ERROR_ACCOUNT_ACTIVATION_EXPIRED;
				}
			} else{
				if(user.getUser_status() == STATUS_USER_NORMAL){
					return Property.ERROR_ACCOUNT_EXIST;
				} else{
					return Property.ERROR_ACCOUNT_ACTIVATION;
				}
				
			}
		}
		return Property.SUCCESS_ACCOUNT_ACTIVATION;
	}
	
	public String findActivationKeyOfUser(int id) {
		return null;
	}
	
	public User findByActivationKey(String key) {
		return userDao.getUser("user_activationKey", new Object[]{key});
	}
	
	public int getStatus(String email) {
		User user = userDao.getUserByEmail(email);
		return user.getUser_status();
	}
	
	/**
	 * 推荐用户
	 * @param count
	 * @return
	 */
	public List<User> getRecommendUsers(int user_id, int count){
		//to do recommend logic
		
		List<User> users = userDao.getUsers(count);
		Iterator<User> it = users.iterator();
		while(it.hasNext()){
			User user = it.next();
			if(user.getId() == user_id) {
				it.remove();
				continue;
			}
			addAvatar(user);
		}

		return users;
	}
	
	public List<Integer> getRecommendUsersID(int user_id, int count) {
		List<User> users = userDao.getUsers(count);
		List<Integer> ids =  new ArrayList<Integer>();
		Iterator<User> it = users.iterator();
		while(it.hasNext()){
			User user = it.next();
			if(user.getId() == user_id) {
				it.remove();
				continue;
			}
			ids.add(user.getId());
		}
		return ids;
	}
	
}
