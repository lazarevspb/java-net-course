package ru.daniilazarnov;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

public class Server implements Runnable {
    private static final Logger log = Logger.getLogger(Server.class);
    private static ServerSocketChannel serverSocketChannel = null;
    private final Selector selector; // слушает все события
    private final ByteBuffer buf = ByteBuffer.allocate(256);
    private int acceptedClientIndex = 1;
    private final ByteBuffer welcomeBuf = ByteBuffer.wrap("Welcome to chat!\n".getBytes());

    Server() throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open(); //создаем ССЧ
        this.serverSocketChannel.socket().bind(new InetSocketAddress(8189)); //устанавливаем порт для ССЧ, который он будет слушать
        this.serverSocketChannel.configureBlocking(false);
        this.selector = Selector.open();
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // регистрация селектора на каннале с определенным ключом акцепт
    }

    public static void main(String[] args) throws IOException {
        start();
    }

    /**
     * Запись полученных данных в файл
     * @param ch канал получаемых данных
     * @param size размер получаемых данных
     * @throws IOException исключение
     */
    private static void writeFile(SocketChannel ch, byte size) throws IOException {
        log.debug("enter writeFile");
        try (FileChannel fileChannel = FileChannel.open(Paths.get("data/_nio-data.txt"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            fileChannel.transferFrom(ch, 0, size);

        }
    }

    /**
     * Стартуем сервер
     * @throws IOException исключение
     */
    private static void start() throws IOException {
        new Thread(new Server()).start(); // запускаем сервер
    }

    @Override
    public void run() {
        try {
            log.info("Сервер запущен (Порт: 8189)");
            Iterator<SelectionKey> iter;
            SelectionKey key;
            while (this.serverSocketChannel.isOpen()) { // внутри вайла канал открыт
                selector.select();// ждем новых событий
                iter = this.selector.selectedKeys().iterator();
                while (iter.hasNext()) { // перебираем ключи в итераторе событий
                    key = iter.next(); //получаем ссылку на событие
                    iter.remove(); //удаляем событие из списка обработки
                    if (key.isAcceptable()) { // проверяем ключ
                        handleAccept(key); // если подключился
                    }
                    if (key.isReadable()) { //если что-то написал
                        handleRead(key);
                    }
                }
            }
        } catch (IOException e) {

            log.error(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    /**
     * Метод подключает новых клиентов
     *
     * @param key Ключ выбора создается каждый раз, когда канал регистрируется с помощью селектора.
     *            Ключ остается действительным до тех пор, пока он не будет отменен вызовом его метода cancel
     * @throws IOException исключение
     */
    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel sc = ((ServerSocketChannel) key.channel()).accept(); // получаем канал из ключа, ссылка на ССК
        String clientName = "client #" + acceptedClientIndex; // присваеваем индекс клиенту
        acceptedClientIndex++;
        sc.configureBlocking(false); // переключение на неблокирующий режим
        sc.register(selector, SelectionKey.OP_READ, clientName);// регистрируем ключ в селекторе на чтение и предаем вместе с именем клиента
        sc.write(welcomeBuf); // посылаем сообщение из велком буфера
        welcomeBuf.rewind(); //сбрасывает позицию буфера на начало, для повторной отправки
        log.info("Подключился новый клиент " + clientName);
    }

    /**
     * Обработка события чтение из канала
     * Чтение из канала
     *
     * @param key ключ
     * @throws IOException исключение
     */
    private void handleRead(SelectionKey key) throws IOException {
        log.debug("handleRead enter");
        SocketChannel ch = (SocketChannel) key.channel(); //получаем ссылку на канал из ключа
        StringBuilder sb = new StringBuilder();
        byte control = getControlByte(ch);
        byte sizeFile = getSizeFile(ch);
        log.debug("control bytes: " + control);

        switch (control) {
            case 70:
                writeFile(ch, sizeFile);
                break;
            case 71:
                getMessage(key, ch, sb);
                break;

            default:
                log.error("Unexpected value: " + control);
//                throw new IllegalStateException("Unexpected value: " + control);
        }
    }

    /**
     * Получаем сообщение
     * @param key ключ
     * @param ch канал
     * @param sb стрингБилдер
     * @throws IOException
     */
    private void getMessage(SelectionKey key, SocketChannel ch, StringBuilder sb) throws IOException {
        buf.clear(); //очищаем буфер
        int read;
        try {
            while ((read = ch.read(buf)) > 0) { //читаем из канала
                buf.flip();
                byte[] bytes = new byte[buf.limit()];
                buf.get(bytes); //записываем в данные из буфера в массив
                sb.append(new String(bytes)); // добавляем в стринг билдер
                log.debug(sb.toString());
                buf.clear();
            }
        } catch (Exception e) {
            key.cancel();
            read = -1;
        }
        String msg;
        if (read < 0) {
            msg = key.attachment() + " покинул чат\n";
            ch.close();
        } else {
            msg = key.attachment() + ": " + sb.toString();
        }
        System.out.println(msg);
        broadcastMessage(msg);
    }


    /**
     * Через этот метод получаем размер файла в байтах из второго байта, метод должен использоваться после
     * @getControlByte иначе будет ошибка в получении служебной информации
     * @param ch
     * @return
     * @throws IOException
     */
    private byte getSizeFile(SocketChannel ch) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        byte size = -1;
        int read;
        buffer.clear(); //очищаем буфер
        if ((read = ch.read(buffer)) > 0) {
            buffer.flip();
            byte[] bytes = new byte[1];
            buffer.get(bytes); //записываем в данные из буфера в массив
            size = bytes[0];
        }
//        log.debug("size: " + size);
        return size;
    }

    /**
     * Получаем первый байт сообщения, который содержит данные о передаваемом контенте.
     *
     * @param ch
     * @return управляющий байт;
     * @throws IOException
     */
    private byte getControlByte(SocketChannel ch) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        byte control = -1;
        int read;
        buffer.clear(); //очищаем буфер
        if ((read = ch.read(buffer)) > 0) {
            buffer.flip();
            byte[] bytes = new byte[1];
            buffer.get(bytes); //записываем в данные из буфера в массив
            control = bytes[0];
        }
        return control;
    }

    /**
     * Рассылаем сообщения всем клиентам
     *
     * @param msg сообщение
     * @throws IOException
     */
    private void broadcastMessage(String msg) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes()); // оборачиваем собщение в байтбуффер
        for (SelectionKey key : selector.keys()) { // перебираем список всех ключей подписавшихся
            if (key.isValid() && key.channel() instanceof SocketChannel) { // если ключи валидные и канал является соектканалом
                SocketChannel sch = (SocketChannel) key.channel(); // получаем ссылку
                sch.write(msgBuf); //отправляем сообщение
                msgBuf.rewind(); // возвращаем буфер в исходное положение
            }
        }
    }
}
