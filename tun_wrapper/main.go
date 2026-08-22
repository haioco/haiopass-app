package main

import (
	"log"
	"os"
	"syscall"

	"golang.org/x/sys/unix"
)

const linker64Path = "/apex/com.android.runtime/bin/linker64"

func main() {
	if len(os.Args) < 4 {
		log.Fatalf("Usage: %s <socket-name> <tun2socks-path> [tun2socks-args...]\n", os.Args[0])
	}

	socketName := os.Args[2]
	tun2socksPath := os.Args[3]
	tun2socksArgs := os.Args[3:]

	log.Printf("tun_wrapper: connecting to socket: %s", socketName)

	tunFd, err := receiveFdViaSocket(socketName)
	if err != nil {
		log.Fatalf("Failed to receive TUN fd: %v", err)
	}
	log.Printf("tun_wrapper: received tunFd=%d", tunFd)

	if err := unix.Dup2(tunFd, 3); err != nil {
		log.Fatalf("dup2(tunFd=%d, 3) failed: %v", tunFd, err)
	}
	log.Printf("tun_wrapper: dup2 done, fd 3 is now TUN fd")

	unix.Close(tunFd)

	for fd := 4; fd <= 1024; fd++ {
		unix.Close(fd)
	}

	log.Printf("tun_wrapper: executing tun2socks via linker64")
	linkerArgs := append([]string{linker64Path, tun2socksPath}, tun2socksArgs[1:]...)
	if err := syscall.Exec(linker64Path, linkerArgs, os.Environ()); err != nil {
		log.Fatalf("exec failed: %v", err)
	}
}

func receiveFdViaSocket(socketName string) (int, error) {
	sock, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		return -1, err
	}
	defer unix.Close(sock)

	addr := &unix.SockaddrUnix{Name: "\x00" + socketName}
	if err := unix.Connect(sock, addr); err != nil {
		return -1, err
	}

	buf := make([]byte, 1)
	oob := make([]byte, unix.CmsgSpace(4))

	n, oobn, _, _, err := unix.Recvmsg(sock, buf, oob, 0)
	if err != nil {
		return -1, err
	}
	_ = n

	msgs, err := unix.ParseSocketControlMessage(oob[:oobn])
	if err != nil {
		return -1, err
	}

	for _, m := range msgs {
		fds, err := unix.ParseUnixRights(&m)
		if err != nil {
			return -1, err
		}
		if len(fds) > 0 {
			return fds[0], nil
		}
	}

	return -1, nil
}
