# Local Development Setup – Serving Schema Files via Apache

This project expects schema files to be available under:

```
/assets/schema/*
```

In development, these files are served directly from the `dcm4chee-arc-lang` project using Apache and a symbolic link.

---

## 1. Create Symlink

Link the `webapp` folder of the language project into Apache’s web root:

```bash
sudo mkdir -p /var/www/html/dcm4chee-arc-lang
sudo ln -s /home/<user>/workspace/dcm4chee-arc-lang/src/main/webapp \
           /var/www/html/dcm4chee-arc-lang/webapp
```

---

## 2. Configure Apache Alias

Edit your Apache site configuration (e.g. `000-default.conf`) and add:

```apache
Alias /dcm4chee-arc-lang/ /var/www/html/dcm4chee-arc-lang/webapp/

<Directory /var/www/html/dcm4chee-arc-lang/webapp>
    Options FollowSymLinks
    Require all granted
</Directory>
```

Reload Apache after saving changes.

---

## 3. Set Permissions

Allow the Apache user (`www-data`) to access the project files:

```bash
sudo usermod -aG <user> www-data
# Optional: restrict access to owner and group only (recommended)
chmod -R 750 /home/<user>/workspace/dcm4chee-arc-lang
```

Restart Apache after applying permissions.

---

## 4. Verify Access

Open in browser:

```
http://localhost/dcm4chee-arc-lang/en/assets/schema/device.schema.json
```

If this works, the setup is correct.

---

## 5. Angular Proxy Configuration

Ensure the Angular proxy maps `/assets/schema` correctly:

```js
"/assets/schema": {
  target: "http://localhost",
  pathRewrite: {
    "^/assets/schema": "/dcm4chee-arc-lang/en/assets/schema"
  }
}
```

---

## Result

* No file copying required
* Changes in `dcm4chee-arc-lang` are reflected immediately
* Works with both Angular dev server and backend deployment

---
