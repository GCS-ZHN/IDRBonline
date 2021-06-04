package org.gcszhn.server;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.github.dockerjava.api.DockerClient;

import org.apache.logging.log4j.Level;
import org.gcszhn.server.ResponseResult.StatusResult;
import org.gcszhn.system.service.DockerService;
import org.gcszhn.system.service.UserService;
import org.gcszhn.system.service.impl.UserServiceImpl;
import org.gcszhn.system.service.obj.DockerNode;
import org.gcszhn.system.service.obj.User;
import org.gcszhn.system.service.obj.UserJob;
import org.gcszhn.system.service.until.AppLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台任务请求控制器
 * @author Zhang.H.N
 * @version 1.0
 */
@RestController
public class JobController {
    /**Docker服务 */
    @Autowired
    DockerService dockerService;
    /**用户服务 */
    @Autowired
    UserService userService;

    /**web请求对象 */
    @Autowired
    HttpServletRequest request;
    /**
     * 提交后台任务
     * @param stdinf 标准输入文件
     * @param stdoutf 标准输出文件
     * @param timeout 超时时间
     * @param cmd 后台命令字符串
     * @return 提交状态
     */
    @GetMapping("/submit")
    public StatusResult doSubmitJob(
        @RequestParam String stdinf, 
        @RequestParam String stdoutf, 
        @RequestParam long timeout,
        @RequestParam String cmd) {

        StatusResult res = new StatusResult();
        res.setStatus(1);
        HttpSession session = request.getSession(false);
        if (session !=null) {
            User user = (User) session.getAttribute("user");
            if (user != null) {
                DockerNode dockerNode = dockerService.getDockerNodeByHost(
                    user.getAliveNode().getHost());
                
                new Thread(
                    ()->{
                       try (DockerClient dockerClient = dockerService.creatClient(
                           UserServiceImpl.getDomain()+"."+dockerNode.getHost(), 
                           dockerNode.getPort(),
                           dockerNode.getApiVersion()
                           )) {
                            /**用户任务实例 */
                            UserJob userJob = new UserJob();
                            userJob.setStatus(1);
                            userJob.setId("background"+userJob.hashCode());
                            userJob.setCmd(cmd);
                            userJob.setUser(user);
                            userJob.setStdinfile(stdinf);
                            userJob.setStdoutfile(stdoutf);

                            FileInputStream stdin = null;
                            if (!stdinf.equals("") && stdinf!=null) stdin = new FileInputStream(
                                "/public/home/"+user.getAccount()
                                + (stdinf.startsWith("/")?stdinf:"/"+stdinf)
                            );
                            //标准错误，固定为一个用户目录下一个随机文件
                            FileOutputStream stderr = new FileOutputStream(
                                "/public/home/"+user.getAccount()+"/"+userJob.getId()+".log");
                            //标准输出，没有指定则合并到标准错误
                            FileOutputStream stdout = null;
                            if (!stdoutf.equals("") && stdoutf!=null) stdout = new FileOutputStream(
                                "/public/home/"+user.getAccount()
                                + (stdoutf.startsWith("/")?stdoutf:"/"+stdoutf)
                            );

                            userService.addUserBackgroundJob(user.getAccount(), userJob);
                            dockerService.execBackgroundJobs(
                                dockerClient,
                                UserServiceImpl.getTagPrefix()+user.getAccount(), 
                                timeout, 
                                TimeUnit.HOURS, 
                                stdin, 
                                stdout, 
                                stderr, 
                                cmd.split("\\s+")
                                );
                            userJob.setStatus(0);
                            userService.removeUserBackgroundJob(user.getAccount(), userJob);
                       } catch (Exception e) {
                           AppLog.printMessage(null, e, Level.ERROR);
                       } 
                    }
                ).start();
                res.setStatus(0);
            } 
        }
        return res;
    }
}