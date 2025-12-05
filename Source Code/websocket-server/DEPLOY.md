# Deploy WebSocket Server lên Azure với SSL/TLS

Server: `40.82.129.148`

## Bước 1: Chuẩn bị trên máy local

```powershell
# Di chuyển vào thư mục websocket-server
cd "C:\Users\nguye\BTl_IOTvaUngDung_Nhom04\Source Code\websocket-server"

# Tạo file zip để upload
Compress-Archive -Path * -DestinationPath websocket-server.zip -Force
```

## Bước 2: Upload code lên server

```powershell
# Upload file zip (thay YOUR_USERNAME bằng username SSH của bạn, thường là azureuser)
scp websocket-server.zip YOUR_USERNAME@40.82.129.148:~/
```

## Bước 3: SSH vào server và setup

```powershell
# SSH vào server
ssh YOUR_USERNAME@40.82.129.148
```

Sau khi SSH vào server, chạy các lệnh sau:

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Cài Docker và Docker Compose
sudo apt install -y docker.io docker-compose
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

# Cài Nginx
sudo apt install -y nginx

# Cài Certbot cho Let's Encrypt SSL
sudo apt install -y certbot python3-certbot-nginx

# Giải nén code
cd ~
unzip -o websocket-server.zip -d websocket-server
cd websocket-server

# Tạo thư mục logs
mkdir -p logs

# Build và chạy Docker
sudo docker-compose up -d --build

# Kiểm tra container đang chạy
sudo docker-compose ps
sudo docker-compose logs -f
```

## Bước 4: Cấu hình Nginx (trên server)

```bash
# Tạo config Nginx cho WebSocket
sudo nano /etc/nginx/sites-available/websocket
```

Paste nội dung này (thay YOUR_DOMAIN.com bằng domain của bạn):

```nginx
# HTTP - redirect to HTTPS
server {
    listen 80;
    server_name YOUR_DOMAIN.com;
    
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }
    
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS - WebSocket Server
server {
    listen 443 ssl http2;
    server_name YOUR_DOMAIN.com;

    # SSL certificates (Certbot sẽ tự thêm sau)
    # ssl_certificate /etc/letsencrypt/live/YOUR_DOMAIN.com/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/YOUR_DOMAIN.com/privkey.pem;

    # SSL settings
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # WebSocket endpoint
    location /ws {
        proxy_pass http://localhost:4000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket timeout settings
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
    }

    # Health check endpoint
    location /health {
        proxy_pass http://localhost:4000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Actuator endpoints
    location /actuator {
        proxy_pass http://localhost:4000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Enable site và restart Nginx:

```bash
# Enable site
sudo ln -s /etc/nginx/sites-available/websocket /etc/nginx/sites-enabled/

# Xóa default site
sudo rm /etc/nginx/sites-enabled/default

# Test config
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
```

## Bước 5: Cài SSL với Certbot (trên server)

```bash
# Chạy Certbot (thay YOUR_DOMAIN.com và YOUR_EMAIL)
sudo certbot --nginx -d YOUR_DOMAIN.com --non-interactive --agree-tos -m YOUR_EMAIL

# Auto-renew SSL
sudo certbot renew --dry-run

# Restart Nginx sau khi có SSL
sudo systemctl restart nginx
```

## Bước 6: Mở firewall (trên server)

```bash
# Mở port 80, 443
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 22/tcp
sudo ufw enable
sudo ufw status
```

## Bước 7: Kiểm tra

Từ máy local:

```powershell
# Test HTTP health (sẽ redirect sang HTTPS)
curl http://YOUR_DOMAIN.com/health

# Test HTTPS health
curl https://YOUR_DOMAIN.com/health

# Test WebSocket từ browser console:
# const ws = new WebSocket('wss://YOUR_DOMAIN.com/ws');
# ws.onopen = () => console.log('Connected!');
# ws.onmessage = (e) => console.log('Received:', e.data);
```

## Bước 8: Xem logs (trên server)

```bash
# Docker logs
sudo docker-compose logs -f

# Nginx logs
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log

# Application logs
tail -f ~/websocket-server/logs/websocket-server.log
```

## Update code sau này

```powershell
# Từ máy local
cd "C:\Users\nguye\BTl_IOTvaUngDung_Nhom04\Source Code\websocket-server"
Compress-Archive -Path * -DestinationPath websocket-server.zip -Force
scp websocket-server.zip YOUR_USERNAME@40.82.129.148:~/

# SSH vào server
ssh YOUR_USERNAME@40.82.129.148

# Trên server
cd ~/websocket-server
sudo docker-compose down
cd ~
unzip -o websocket-server.zip -d websocket-server
cd websocket-server
sudo docker-compose up -d --build
sudo docker-compose logs -f
```

## Nếu không có domain (dùng IP)

Bỏ qua bước SSL/Certbot và dùng config Nginx đơn giản:

```bash
sudo nano /etc/nginx/sites-available/websocket
```

```nginx
server {
    listen 80;
    server_name 40.82.129.148;

    location /ws {
        proxy_pass http://localhost:4000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }

    location /health {
        proxy_pass http://localhost:4000;
    }
}
```

WebSocket URL: `ws://40.82.129.148/ws`
