package com.zxsoft.crawler.entity;

// Generated 2014-9-19 17:19:57 by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;


/**
 * Account generated by hbm2java
 */
@Entity
@Table(name = "auth", catalog = "crawler")
public class Auth implements java.io.Serializable {

	/**
	 * 
	 */
    private static final long serialVersionUID = 5502242494979474026L;
	private String id;
	private String username;
	private String password;
	private Website website;

	public Auth() {
	}

	public Auth(String id, String username, String password) {
		this.id = id;
		this.username = username;
		this.password = password;
	}

	public Auth(String id, String username, String password, Website website) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.website = website;
	}

	@Id
	@Column(name = "id", unique = true, nullable = false, length = 100)
	@GenericGenerator(name = "generator", strategy = "uuid.hex")
	@GeneratedValue(generator = "generator")
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Column(name = "username", nullable = false, length = 100)
	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Column(name = "password", nullable = false, length = 100)
	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "siteId")
	public Website getWebsite() {
		return this.website;
	}

	public void setWebsite(Website website) {
		this.website = website;
	}

}
