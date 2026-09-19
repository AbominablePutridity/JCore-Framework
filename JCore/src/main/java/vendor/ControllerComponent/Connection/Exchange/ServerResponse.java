package vendor.ControllerComponent.Connection.Exchange;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Класс с данными для ответа серверу.
 * 
 * @author User
 */
public class ServerResponse {
    private Data data;

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
    
    // переводит данные в байты и кладет их в поток
    public void convertDataToBytesForStream(OutputStream out) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);

        // 1. Текстовая часть: params<endl>params<endl>...<BINARY>
        StringBuilder paramLine = new StringBuilder();
        
        String[] params = data.getParams();
        if (params != null) {
            for (String p : params) {
                paramLine.append(p).append("<endl>");
            }
        }
        paramLine.append("<BINARY>");
        dout.write(paramLine.toString().getBytes(StandardCharsets.UTF_8));

        // 2. Бинарная часть: файлы кусками [флаг][длина][данные]
        byte[] buf = new byte[64 * 1024];
        File[] binaryFiles = data.getBinaryFiles();

        if (binaryFiles != null) {
            for (File file : binaryFiles) {

                // Пустой файл пропускаем: [0][0] — это маркер конца списка, а не пустой файл
                if (file.length() == 0) {
                    continue;
                }

                try (PushbackInputStream in =
                         new PushbackInputStream(Files.newInputStream(file.toPath()), 1)) {

                    while (true) {
                        int read = in.read(buf);
                        if (read == -1) {
                            break;
                        }

                        boolean hasNext;
                        if (read < buf.length) {
                            hasNext = false;
                        } else {
                            int probe = in.read();
                            if (probe == -1) {
                                hasNext = false;
                            } else {
                                hasNext = true;
                                in.unread(probe);
                            }
                        }

                        dout.writeByte(hasNext ? 1 : 0);
                        dout.writeInt(read);
                        dout.write(buf, 0, read);

                        if (!hasNext) {
                            break;
                        }
                    }
                }
            }
        }

        // 3. Маркер конца списка файлов: [0][0][0][0][0]
        dout.writeByte(0);
        dout.writeInt(0);

        dout.flush();
        //return out;
    }
}
