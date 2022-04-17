import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 主程序
 */
public class Main {
    public static final Set<ClientUser> clientUserSet = new HashSet<>();

    public static void main(String[] args){
        try (ServerSocket s = new ServerSocket(10002)){
            while (true) {
                Socket socket = s.accept();
                Thread t = new Thread(new HandleTask(socket));
                t.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
/**
 * 处理文件下载的任务
 */
class HandleTask implements Runnable {
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private ClientUser user;
    private static final String BASE_DIR = "D:\\idea工程文件\\document";
    public HandleTask(Socket socket) {
        this.socket = socket;
        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void run(){
        try {
            while (true) {
                int code = inputStream.readInt();
                switch (code) {
                    case Status.REGISTER_CODE:
                        outputStream.writeBoolean(register());
                        break;
                    case Status.LOGIN_CODE:
                        ClientUser user = login();
                        if(user == null)
                            outputStream.writeBoolean(false);
                        else
                            outputStream.writeBoolean(true);
                        this.user = user;
                        break;
                    case Status.UPFILE_CODE:
                        outputStream.writeBoolean(upFile(this.user, true));
                        break;
                    case Status.DOWNFILE_CODE:
                        downFile(this.user);
                        break;
                    case Status.LISTUSERFILE_CODE:
                        listUserFileIO(this.user);
                        break;
                    case Status.QUIT:
                        System.out.println(this.user.getAccount() + "已注销");
                        this.user = null;
                        outputStream.writeBoolean(true);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*try(DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            String filename = inputStream.readUTF();
            System.out.println("客户端正在上传文件：" + filename);
            Path desPath = Paths.get(BASE_DIR, filename);
            if (!Files.exists(desPath.getParent()))
                Files.createDirectories(desPath.getParent());
            if (!Files.exists(desPath))
                Files.createFile(desPath);
            byte[] buffer = new byte[1024];
            try(OutputStream outputStream1 = Files.newOutputStream(desPath)) {

                while (inputStream.read(buffer) != -1) {
                    outputStream1.write(buffer);
                }
            }
            outputStream.writeUTF("文件上传完成");
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    private boolean register() throws IOException{
        String account = inputStream.readUTF();
        String password = inputStream.readUTF();
        ClientUser user = new ClientUser(account, password);

        if (Main.clientUserSet.contains(user)) {
            System.out.println(account+ "已存在，注册失败");
            return false;
        } else {
            Main.clientUserSet.add(user);
            Path userDir = Paths.get(BASE_DIR, user.getAccount());
            if(!Files.exists(userDir))
                Files.createDirectories(userDir);
            System.out.println(account + "注册成功");
            return true;
        }

    }

    private ClientUser login() throws IOException {
        String account = inputStream.readUTF();
        String password = inputStream.readUTF();
        ClientUser user = new ClientUser(account, password);
        if (Main.clientUserSet.contains(user)){
            System.out.println(account + "登录成功");
            return user;
        } else
            System.out.println(account + "登录失败");
            return null;
    }

    /**
     * 客户端上传文件
     * @return 返回上传是否成功，用来反馈给客户端
     * @throws IOException
     */
    private boolean upFile(ClientUser user, boolean covered) throws IOException {
        String filename = inputStream.readUTF();
        System.out.println("账户：" + user.getAccount() + "正在上传文件：" + filename);
        Path userFile = Paths.get(BASE_DIR, user.getAccount(), filename);
        if(!Files.exists(userFile.getParent())) {
            Files.createDirectories(userFile.getParent());
        }
        if(!Files.exists(userFile)) {
            Files.createFile(userFile);
        } else if(!covered) {
            return false;//不覆盖且文件存在则上传文件失败
        }
        try(OutputStream fileOutputStream = Files.newOutputStream(userFile);) {
            byte[] buffer = new byte[4096];
            byte[] EOF = "文件结束".getBytes(StandardCharsets.UTF_8);
            byte[] received = new byte[4096];
            for (int i = 0; i < EOF.length; i++) {
                received[i] = EOF[i];
            }
            int available = inputStream.available();
            while ((inputStream.read(buffer)) != -1 && !new String(buffer, "utf-8")
                    .equals(new String(received, "utf-8"))) {
                if(available > 4096){
                    fileOutputStream.write(buffer);
                } else {
                    fileOutputStream.write(buffer, 0, available);
                }
                available = inputStream.available();
            }
            System.out.println("账户：" + user.getAccount() + "上传文件完成：" + filename);
            return true;
        }

    }

    /**
     * 客户端下载文件
     * @return 下载成功与否返回给客户端
     * @throws IOException
     */
    private boolean downFile(ClientUser user) throws IOException {
        //将服务端已有文件展示出来
        ArrayList<Path> filepaths = listUserFile(user);
        if(filepaths.size() == 0){
            outputStream.writeBoolean(false);
            return false;
        }
        outputStream.writeBoolean(true);
        outputStream.writeInt(filepaths.size());
        for(int i = 0; i < filepaths.size(); i++) {
            outputStream.writeUTF((i+1) + "." +filepaths.get(i).getFileName().toString());
        }
        outputStream.writeUTF("END");
        //结束----------------
        int fileindex = inputStream.readInt()-1;
        String filename = filepaths.get(fileindex).getFileName().toString();
        System.out.println("账户：" + user.getAccount() + "正在下载文件：" + filename);
        Path userFile = filepaths.get(fileindex);

        //将文件是否存在告知客户端
        if(!Files.exists(userFile)) {
            outputStream.writeBoolean(false);
            return false;
        }
        outputStream.writeBoolean(true);

        try(InputStream fileInputStream = Files.newInputStream(userFile)) {
            //构造结束符
            byte[] EOF = "文件结束".getBytes(StandardCharsets.UTF_8);
            byte[] sended = new byte[4096];
            for (int i = 0; i < EOF.length; i++) {
                sended[i] = EOF[i];
            }

            byte[] buffer = new byte[4096];
            while ((fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer);
            }
            //最后加上结束符
            outputStream.write(sended);
            boolean result = inputStream.readBoolean();
            if (result){
                System.out.println("账户：" + user.getAccount() + "下载文件完成：" + filename);
                return true;
            }
            System.out.println("账户：" + user.getAccount() + "下载文件失败：" + filename);
            return false;
        }
    }

    /**
     * 返回用户
     * @param user
     * @return
     * @throws IOException
     */
    private ArrayList<Path> listUserFile(ClientUser user) throws IOException{
        Path userDir = Paths.get(BASE_DIR, user.getAccount());
        ArrayList<Path> filepaths = new ArrayList<>();
        try(Stream<Path> entries = Files.walk(userDir)) {
            entries.forEach(file -> {
                if(!Files.isDirectory(file)){
                    filepaths.add(file);
                }
            });
        }
        /*if (filenames.size() == 0) {
            outputStream.writeBoolean(false);
        } else {
            outputStream.writeBoolean(true);
            outputStream.writeInt(filenames.size());
            for(int i = 0; i < filenames.size(); i++) {
                outputStream.writeUTF((i+1) + "." +filenames.get(i));
            }
            outputStream.writeUTF("END");
        }*/
        return filepaths;
    }
    private void listUserFileIO(ClientUser user) throws IOException{
        Path userDir = Paths.get(BASE_DIR, user.getAccount());
        ArrayList<Path> filepaths = new ArrayList<>();
        try(Stream<Path> entries = Files.walk(userDir)) {
            entries.forEach(file -> {
                if(!Files.isDirectory(file)){
                    filepaths.add(file);
                }
            });
        }
        if (filepaths.size() == 0) {
            outputStream.writeBoolean(false);
            return;
        } else {
            outputStream.writeBoolean(true);
            outputStream.writeInt(filepaths.size());
            for(int i = 0; i < filepaths.size(); i++) {
                outputStream.writeUTF((i+1) + "." +filepaths.get(i).getFileName().toString());
            }
            outputStream.writeUTF("END");
        }
    }
}

class Status {
    public static final int REGISTER_CODE = 1;
    public static final int LOGIN_CODE = 2;
    public static final int UPFILE_CODE = 3;
    public static final int DOWNFILE_CODE = 4;
    public static final int LISTUSERFILE_CODE = 5;
    public static final int QUIT = 6;
}